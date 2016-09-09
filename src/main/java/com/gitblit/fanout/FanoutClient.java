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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fanout client class.
 *
 * @author James Moger
 *
 */
public class FanoutClient implements Runnable {

	private final static Logger logger = LoggerFactory.getLogger(FanoutClient.class);

	private final int clientTimeout = 500;
	private final int reconnectTimeout = 2000;
	private final String host;
	private final int port;
	private final List<FanoutListener> listeners;

	private String id;
	private volatile Selector selector;
	private volatile SocketChannel socketCh;
	private Thread clientThread;

	private final AtomicBoolean isConnected;
	private final AtomicBoolean isRunning;
	private final AtomicBoolean isAutomaticReconnect;
	private final ByteBuffer writeBuffer;
	private final ByteBuffer readBuffer;
	private final CharsetDecoder decoder;

	private final Set<String> subscriptions;
	private boolean resubscribe;

	public interface FanoutListener {
		public void pong(Date timestamp);

		public void announcement(String channel, String message);
	}

	public static class FanoutAdapter implements FanoutListener {
		@Override
		public void pong(Date timestamp) {
		}

		@Override
		public void announcement(String channel, String message) {
		}
	}

	public static void main(String args[]) throws Exception {
		final FanoutClient client = new FanoutClient("localhost", 2000);
		client.addListener(new FanoutAdapter() {

			@Override
			public void pong(Date timestamp) {
				System.out.println("Pong. " + timestamp);
			}

			@Override
			public void announcement(String channel, String message) {
				System.out.println(MessageFormat.format("Here ye, Here ye. {0} says {1}", channel,
						message));
			}
		});
		client.start();

		Thread.sleep(5000);
		client.ping();
		client.subscribe("james");
		client.announce("james", "12345");
		client.subscribe("c52f99d16eb5627877ae957df7ce1be102783bd5");

		while (true) {
			Thread.sleep(10000);
			client.ping();
		}
	}

	public FanoutClient(String host, int port) {
		this.host = host;
		this.port = port;
		this.readBuffer = ByteBuffer.allocateDirect(FanoutConstants.BUFFER_LENGTH);
		this.writeBuffer = ByteBuffer.allocateDirect(FanoutConstants.BUFFER_LENGTH);
		this.decoder = Charset.forName(FanoutConstants.CHARSET).newDecoder();
		this.listeners = Collections.synchronizedList(new ArrayList<FanoutListener>());
		this.subscriptions = new LinkedHashSet<String>();
		this.isRunning = new AtomicBoolean(false);
		this.isConnected = new AtomicBoolean(false);
		this.isAutomaticReconnect = new AtomicBoolean(true);
	}

	public void addListener(FanoutListener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(FanoutListener listener) {
		this.listeners.remove(listener);
	}

	public boolean isAutomaticReconnect() {
		return this.isAutomaticReconnect.get();
	}

	public void setAutomaticReconnect(boolean value) {
		this.isAutomaticReconnect.set(value);
	}

	public void ping() {
		confirmConnection();
		write("ping");
	}

	public void status() {
		confirmConnection();
		write("status");
	}

	public void subscribe(String channel) {
		confirmConnection();
		if (this.subscriptions.add(channel)) {
			write("subscribe " + channel);
		}
	}

	public void unsubscribe(String channel) {
		confirmConnection();
		if (this.subscriptions.remove(channel)) {
			write("unsubscribe " + channel);
		}
	}

	public void announce(String channel, String message) {
		confirmConnection();
		write("announce " + channel + " " + message);
	}

	private void confirmConnection() {
		if (!isConnected()) {
			throw new RuntimeException("Fanout client is disconnected!");
		}
	}

	public boolean isConnected() {
		return this.isRunning.get() && (this.socketCh != null) && this.isConnected.get();
	}

	/**
	 * Start client connection and return immediately.
	 */
	public void start() {
		if (this.isRunning.get()) {
			logger.warn("Fanout client is already running");
			return;
		}
		this.clientThread = new Thread(this, "Fanout client");
		this.clientThread.start();
	}

	/**
	 * Start client connection and wait until it has connected.
	 */
	public void startSynchronously() {
		start();
		while (!isConnected()) {
			try {
				Thread.sleep(100);
			}
			catch (final Exception e) {
			}
		}
	}

	/**
	 * Stops client connection. This method returns when the connection has been
	 * completely shutdown.
	 */
	public void stop() {
		if (!this.isRunning.get()) {
			logger.warn("Fanout client is not running");
			return;
		}
		this.isRunning.set(false);
		try {
			if (this.clientThread != null) {
				this.clientThread.join();
				this.clientThread = null;
			}
		}
		catch (final InterruptedException e1) {
		}
	}

	@Override
	public void run() {
		resetState();

		this.isRunning.set(true);
		while (this.isRunning.get()) {
			// (re)connect
			if (this.socketCh == null) {
				try {
					final InetAddress addr = InetAddress.getByName(this.host);
					this.socketCh = SocketChannel.open(new InetSocketAddress(addr, this.port));
					this.socketCh.configureBlocking(false);
					this.selector = Selector.open();
					this.id = FanoutConstants.getLocalSocketId(this.socketCh.socket());
					this.socketCh.register(this.selector, SelectionKey.OP_READ);
				}
				catch (final Exception e) {
					logger.error(MessageFormat.format(
							"failed to open client connection to {0}:{1,number,0}", this.host,
							this.port), e);
					try {
						Thread.sleep(this.reconnectTimeout);
					}
					catch (final InterruptedException x) {
					}
					continue;
				}
			}

			// read/write
			try {
				this.selector.select(this.clientTimeout);

				final Iterator<SelectionKey> i = this.selector.selectedKeys().iterator();
				while (i.hasNext()) {
					final SelectionKey key = i.next();
					i.remove();

					if (key.isReadable()) {
						// read message
						final String content = read();
						final String[] lines = content.split("\n");
						for (final String reply : lines) {
							logger.trace(MessageFormat.format("fanout client {0} received: {1}",
									this.id, reply));
							if (!processReply(reply)) {
								logger.error(MessageFormat.format(
										"fanout client {0} received unknown message", this.id));
							}
						}
					} else if (key.isWritable()) {
						// resubscribe
						if (this.resubscribe) {
							this.resubscribe = false;
							logger.info(MessageFormat.format(
									"fanout client {0} re-subscribing to {1} channels", this.id,
									this.subscriptions.size()));
							for (final String subscription : this.subscriptions) {
								write("subscribe " + subscription);
							}
						}
						this.socketCh.register(this.selector, SelectionKey.OP_READ);
					}
				}
			}
			catch (final IOException e) {
				logger.error(MessageFormat.format("fanout client {0} error: {1}", this.id,
						e.getMessage()));
				closeChannel();
				if (!this.isAutomaticReconnect.get()) {
					this.isRunning.set(false);
					continue;
				}
			}
		}

		closeChannel();
		resetState();
	}

	protected void resetState() {
		this.readBuffer.clear();
		this.writeBuffer.clear();
		this.isRunning.set(false);
		this.isConnected.set(false);
	}

	private void closeChannel() {
		try {
			if (this.socketCh != null) {
				this.socketCh.close();
				this.socketCh = null;
				this.selector.close();
				this.selector = null;
				this.isConnected.set(false);
			}
		}
		catch (final IOException x) {
		}
	}

	protected boolean processReply(String reply) {
		final String[] fields = reply.split("!", 2);
		if (fields.length == 1) {
			try {
				final long time = Long.parseLong(fields[0]);
				final Date date = new Date(time);
				firePong(date);
			}
			catch (final Exception e) {
			}
			return true;
		} else if (fields.length == 2) {
			final String channel = fields[0];
			final String message = fields[1];
			if (FanoutConstants.CH_DEBUG.equals(channel)) {
				// debug messages are for internal use
				if (FanoutConstants.MSG_CONNECTED.equals(message)) {
					this.isConnected.set(true);
					this.resubscribe = this.subscriptions.size() > 0;
					if (this.resubscribe) {
						try {
							// register for async resubscribe
							this.socketCh.register(this.selector, SelectionKey.OP_WRITE);
						}
						catch (final Exception e) {
							logger.error("an error occurred", e);
						}
					}
				}
				logger.debug(MessageFormat.format("fanout client {0} < {1}", this.id, reply));
			} else {
				fireAnnouncement(channel, message);
			}
			return true;
		} else {
			// unknown message
			return false;
		}
	}

	protected void firePong(Date timestamp) {
		logger.info(MessageFormat.format("fanout client {0} < pong {1,date,yyyy-MM-dd HH:mm:ss}",
				this.id, timestamp));
		for (final FanoutListener listener : this.listeners) {
			try {
				listener.pong(timestamp);
			}
			catch (final Throwable t) {
				logger.error("FanoutListener threw an exception!", t);
			}
		}
	}

	protected void fireAnnouncement(String channel, String message) {
		logger.info(MessageFormat.format("fanout client {0} < announcement {1} {2}", this.id,
				channel, message));
		for (final FanoutListener listener : this.listeners) {
			try {
				listener.announcement(channel, message);
			}
			catch (final Throwable t) {
				logger.error("FanoutListener threw an exception!", t);
			}
		}
	}

	protected synchronized String read() throws IOException {
		this.readBuffer.clear();
		final long len = this.socketCh.read(this.readBuffer);

		if (len == -1) {
			logger.error(MessageFormat.format(
					"fanout client {0} lost connection to {1}:{2,number,0}, end of stream",
					this.id, this.host, this.port));
			this.socketCh.close();
			return null;
		} else {
			this.readBuffer.flip();
			final String content = this.decoder.decode(this.readBuffer).toString();
			this.readBuffer.clear();
			return content;
		}
	}

	protected synchronized boolean write(String message) {
		try {
			logger.info(MessageFormat.format("fanout client {0} > {1}", this.id, message));
			final byte[] bytes = message.getBytes(FanoutConstants.CHARSET);
			this.writeBuffer.clear();
			this.writeBuffer.put(bytes);
			if (bytes[bytes.length - 1] != 0xa) {
				this.writeBuffer.put((byte) 0xa);
			}
			this.writeBuffer.flip();

			// loop until write buffer has been completely sent
			long written = 0;
			final long toWrite = this.writeBuffer.remaining();
			while (written != toWrite) {
				written += this.socketCh.write(this.writeBuffer);
				try {
					Thread.sleep(10);
				}
				catch (final Exception x) {
				}
			}
			return true;
		}
		catch (final IOException e) {
			logger.error("fanout client {0} error: {1}", this.id, e.getMessage());
		}
		return false;
	}
}