/*
 * Copyright 2013 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.fanout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single-thread NIO implementation of https://github.com/travisghansen/fanout
 *
 * This implementation uses channels and selectors, which are the Java analog of
 * the Linux epoll mechanism used in the original fanout C code.
 *
 * @author James Moger
 *
 */
public class FanoutNioService extends FanoutService {

	private final static Logger logger = LoggerFactory.getLogger(FanoutNioService.class);

	private volatile ServerSocketChannel serviceCh;
	private volatile Selector selector;

	public static void main(String[] args) {
		final FanoutNioService pubsub = new FanoutNioService(null, DEFAULT_PORT);
		pubsub.setStrictRequestTermination(false);
		pubsub.setAllowAllChannelAnnouncements(false);
		pubsub.start();
	}

	/**
	 * Create a single-threaded fanout service.
	 *
	 * @param host
	 * @param port
	 *            the port for running the fanout PubSub service
	 * @throws IOException
	 */
	public FanoutNioService(int port) {
		this(null, port);
	}

	/**
	 * Create a single-threaded fanout service.
	 *
	 * @param bindInterface
	 *            the ip address to bind for the service, may be null
	 * @param port
	 *            the port for running the fanout PubSub service
	 * @throws IOException
	 */
	public FanoutNioService(String bindInterface, int port) {
		super(bindInterface, port, "Fanout nio service");
	}

	@Override
	protected boolean isConnected() {
		return this.serviceCh != null;
	}

	@Override
	protected boolean connect() {
		if (this.serviceCh == null) {
			try {
				this.serviceCh = ServerSocketChannel.open();
				this.serviceCh.configureBlocking(false);
				this.serviceCh.socket().setReuseAddress(true);
				this.serviceCh.socket().bind(
						this.host == null ? new InetSocketAddress(this.port)
								: new InetSocketAddress(this.host, this.port));
				this.selector = Selector.open();
				this.serviceCh.register(this.selector, SelectionKey.OP_ACCEPT);
				logger.info(MessageFormat.format("{0} is ready on {1}:{2,number,0}", this.name,
						this.host == null ? "0.0.0.0" : this.host, this.port));
			}
			catch (final IOException e) {
				logger.error(
						MessageFormat.format("failed to open {0} on {1}:{2,number,0}", this.name,
								this.name, this.host == null ? "0.0.0.0" : this.host, this.port), e);
				return false;
			}
		}
		return true;
	}

	@Override
	protected void disconnect() {
		try {
			if (this.serviceCh != null) {
				// close all active client connections
				final Map<String, SocketChannel> clients = getCurrentClientSockets();
				for (final Map.Entry<String, SocketChannel> client : clients.entrySet()) {
					closeClientSocket(client.getKey(), client.getValue());
				}

				// close service socket channel
				logger.debug(MessageFormat.format("closing {0} socket channel", this.name));
				this.serviceCh.socket().close();
				this.serviceCh.close();
				this.serviceCh = null;
				this.selector.close();
				this.selector = null;
			}
		}
		catch (final IOException e) {
			logger.error(MessageFormat.format("failed to disconnect {0}", this.name), e);
		}
	}

	@Override
	protected void listen() throws IOException {
		while (this.selector.select(serviceTimeout) > 0) {
			final Set<SelectionKey> keys = this.selector.selectedKeys();
			final Iterator<SelectionKey> keyItr = keys.iterator();
			while (keyItr.hasNext()) {
				final SelectionKey key = keyItr.next();
				if (key.isAcceptable()) {
					// new fanout client connection
					final ServerSocketChannel sch = (ServerSocketChannel) key.channel();
					try {
						final SocketChannel ch = sch.accept();
						ch.configureBlocking(false);
						configureClientSocket(ch.socket());

						final FanoutNioConnection connection = new FanoutNioConnection(ch);
						addConnection(connection);

						// register to send the queued message
						ch.register(this.selector, SelectionKey.OP_WRITE, connection);
					}
					catch (final IOException e) {
						logger.error("error accepting fanout connection", e);
					}
				} else if (key.isReadable()) {
					// read fanout client request
					final SocketChannel ch = (SocketChannel) key.channel();
					final FanoutNioConnection connection = (FanoutNioConnection) key.attachment();
					try {
						connection.read(ch, isStrictRequestTermination());
						int replies = 0;
						final Iterator<String> reqItr = connection.requestQueue.iterator();
						while (reqItr.hasNext()) {
							final String req = reqItr.next();
							final String reply = processRequest(connection, req);
							reqItr.remove();
							if (reply != null) {
								replies++;
							}
						}

						if (replies > 0) {
							// register to send the replies to requests
							ch.register(this.selector, SelectionKey.OP_WRITE, connection);
						} else {
							// re-register for next read
							ch.register(this.selector, SelectionKey.OP_READ, connection);
						}
					}
					catch (final IOException e) {
						logger.error(MessageFormat.format("fanout connection {0} error: {1}",
								connection.id, e.getMessage()));
						removeConnection(connection);
						closeClientSocket(connection.id, ch);
					}
				} else if (key.isWritable()) {
					// asynchronous reply to fanout client request
					final SocketChannel ch = (SocketChannel) key.channel();
					final FanoutNioConnection connection = (FanoutNioConnection) key.attachment();
					try {
						connection.write(ch);

						if (hasConnection(connection)) {
							// register for next read
							ch.register(this.selector, SelectionKey.OP_READ, connection);
						} else {
							// Connection was rejected due to load or
							// some other reason. Close it.
							closeClientSocket(connection.id, ch);
						}
					}
					catch (final IOException e) {
						logger.error(MessageFormat.format("fanout connection {0}: {1}",
								connection.id, e.getMessage()));
						removeConnection(connection);
						closeClientSocket(connection.id, ch);
					}
				}
				keyItr.remove();
			}
		}
	}

	protected void closeClientSocket(String id, SocketChannel ch) {
		try {
			ch.close();
		}
		catch (final IOException e) {
			logger.error(MessageFormat.format("fanout connection {0}", id), e);
		}
	}

	@Override
	protected void broadcast(Collection<FanoutServiceConnection> connections, String channel,
			String message) {
		super.broadcast(connections, channel, message);

		// register queued write
		final Map<String, SocketChannel> sockets = getCurrentClientSockets();
		for (final FanoutServiceConnection connection : connections) {
			final SocketChannel ch = sockets.get(connection.id);
			if (ch == null) {
				logger.warn(MessageFormat.format("fanout connection {0} has been disconnected",
						connection.id));
				removeConnection(connection);
				continue;
			}
			try {
				ch.register(this.selector, SelectionKey.OP_WRITE, connection);
			}
			catch (final IOException e) {
				logger.error(MessageFormat.format(
						"failed to register write op for fanout connection {0}", connection.id));
			}
		}
	}

	protected Map<String, SocketChannel> getCurrentClientSockets() {
		final Map<String, SocketChannel> sockets = new HashMap<String, SocketChannel>();
		for (final SelectionKey key : this.selector.keys()) {
			if (key.channel() instanceof SocketChannel) {
				final SocketChannel ch = (SocketChannel) key.channel();
				final String id = FanoutConstants.getRemoteSocketId(ch.socket());
				sockets.put(id, ch);
			}
		}
		return sockets;
	}

	/**
	 * FanoutNioConnection handles reading/writing messages from a remote fanout
	 * connection.
	 *
	 * @author James Moger
	 *
	 */
	static class FanoutNioConnection extends FanoutServiceConnection {
		final ByteBuffer readBuffer;
		final ByteBuffer writeBuffer;
		final List<String> requestQueue;
		final List<String> replyQueue;
		final CharsetDecoder decoder;

		FanoutNioConnection(SocketChannel ch) {
			super(ch.socket());
			this.readBuffer = ByteBuffer.allocate(FanoutConstants.BUFFER_LENGTH);
			this.writeBuffer = ByteBuffer.allocate(FanoutConstants.BUFFER_LENGTH);
			this.requestQueue = new ArrayList<String>();
			this.replyQueue = new ArrayList<String>();
			this.decoder = Charset.forName(FanoutConstants.CHARSET).newDecoder();
		}

		protected void read(SocketChannel ch, boolean strictRequestTermination)
				throws CharacterCodingException, IOException {
			long bytesRead = 0;
			this.readBuffer.clear();
			bytesRead = ch.read(this.readBuffer);
			this.readBuffer.flip();
			if (bytesRead == -1) {
				throw new IOException("lost client connection, end of stream");
			}
			if (this.readBuffer.limit() == 0) {
				return;
			}
			final CharBuffer cbuf = this.decoder.decode(this.readBuffer);
			final String req = cbuf.toString();
			final String[] lines = req.split(strictRequestTermination ? "\n" : "\n|\r");
			this.requestQueue.addAll(Arrays.asList(lines));
		}

		protected void write(SocketChannel ch) throws IOException {
			final Iterator<String> itr = this.replyQueue.iterator();
			while (itr.hasNext()) {
				final String reply = itr.next();
				this.writeBuffer.clear();
				logger.debug(MessageFormat.format("fanout reply to {0}: {1}", this.id, reply));
				final byte[] bytes = reply.getBytes(FanoutConstants.CHARSET);
				this.writeBuffer.put(bytes);
				if (bytes[bytes.length - 1] != 0xa) {
					this.writeBuffer.put((byte) 0xa);
				}
				this.writeBuffer.flip();

				// loop until write buffer has been completely sent
				int written = 0;
				final int toWrite = this.writeBuffer.remaining();
				while (written != toWrite) {
					written += ch.write(this.writeBuffer);
					try {
						Thread.sleep(10);
					}
					catch (final Exception x) {
					}
				}
				itr.remove();
			}
			this.writeBuffer.clear();
		}

		@Override
		protected void reply(String content) throws IOException {
			// queue the reply
			// replies are transmitted asynchronously from the requests
			this.replyQueue.add(content);
		}
	}
}