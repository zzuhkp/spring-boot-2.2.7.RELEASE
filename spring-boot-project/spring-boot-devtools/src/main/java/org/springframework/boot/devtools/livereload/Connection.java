/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.devtools.livereload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.log.LogMessage;
import org.springframework.util.Base64Utils;

/**
 * A {@link LiveReloadServer} connection.
 *
 * @author Phillip Webb
 */
class Connection {

	private static final Log logger = LogFactory.getLog(Connection.class);

	private static final Pattern WEBSOCKET_KEY_PATTERN = Pattern.compile("^Sec-WebSocket-Key:(.*)$", Pattern.MULTILINE);

	public static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	/**
	 * 表示客户端的 Socket
	 */
	private final Socket socket;

	/**
	 * 客户端 Socket 对应的输入流
	 */
	private final ConnectionInputStream inputStream;

	/**
	 * 客户端 Socket 对应的输出流
	 */
	private final ConnectionOutputStream outputStream;

	/**
	 * HTTP 请求头
	 */
	private final String header;

	private volatile boolean webSocket;

	private volatile boolean running = true;

	/**
	 * Create a new {@link Connection} instance.
	 *
	 * @param socket       the source socket
	 * @param inputStream  the socket input stream
	 * @param outputStream the socket output stream
	 * @throws IOException in case of I/O errors
	 */
	Connection(Socket socket, InputStream inputStream, OutputStream outputStream) throws IOException {
		this.socket = socket;
		this.inputStream = new ConnectionInputStream(inputStream);
		this.outputStream = new ConnectionOutputStream(outputStream);
		this.header = this.inputStream.readHeader();
		logger.debug(LogMessage.format("Established livereload connection [%s]", this.header));
	}

	/**
	 * Run the connection.
	 *
	 * @throws Exception in case of errors
	 */
	void run() throws Exception {
		if (this.header.contains("Upgrade: websocket") && this.header.contains("Sec-WebSocket-Version: 13")) {
			runWebSocket();
		}
		if (this.header.contains("GET /livereload.js")) {
			// 写响应
			this.outputStream.writeHttp(getClass().getResourceAsStream("livereload.js"), "text/javascript");
		}
	}

	private void runWebSocket() throws Exception {
		String accept = getWebsocketAcceptResponse();
		this.outputStream.writeHeaders("HTTP/1.1 101 Switching Protocols", "Upgrade: websocket", "Connection: Upgrade",
				"Sec-WebSocket-Accept: " + accept);
		new Frame("{\"command\":\"hello\",\"protocols\":[\"http://livereload.com/protocols/official-7\"],"
				+ "\"serverName\":\"spring-boot\"}").write(this.outputStream);
		Thread.sleep(100);
		this.webSocket = true;
		while (this.running) {
			readWebSocketFrame();
		}
	}

	private void readWebSocketFrame() throws IOException {
		try {
			Frame frame = Frame.read(this.inputStream);
			if (frame.getType() == Frame.Type.PING) {
				writeWebSocketFrame(new Frame(Frame.Type.PONG));
			} else if (frame.getType() == Frame.Type.CLOSE) {
				throw new ConnectionClosedException();
			} else if (frame.getType() == Frame.Type.TEXT) {
				logger.debug(LogMessage.format("Received LiveReload text frame %s", frame));
			} else {
				throw new IOException("Unexpected Frame Type " + frame.getType());
			}
		} catch (SocketTimeoutException ex) {
			writeWebSocketFrame(new Frame(Frame.Type.PING));
			Frame frame = Frame.read(this.inputStream);
			if (frame.getType() != Frame.Type.PONG) {
				throw new IllegalStateException("No Pong");
			}
		}
	}

	/**
	 * Trigger livereload for the client using this connection.
	 *
	 * @throws IOException in case of I/O errors
	 */
	void triggerReload() throws IOException {
		if (this.webSocket) {
			logger.debug("Triggering LiveReload");
			writeWebSocketFrame(new Frame("{\"command\":\"reload\",\"path\":\"/\"}"));
		}
	}

	private void writeWebSocketFrame(Frame frame) throws IOException {
		frame.write(this.outputStream);
	}

	private String getWebsocketAcceptResponse() throws NoSuchAlgorithmException {
		Matcher matcher = WEBSOCKET_KEY_PATTERN.matcher(this.header);
		if (!matcher.find()) {
			throw new IllegalStateException("No Sec-WebSocket-Key");
		}
		String response = matcher.group(1).trim() + WEBSOCKET_GUID;
		MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
		messageDigest.update(response.getBytes(), 0, response.length());
		return Base64Utils.encodeToString(messageDigest.digest());
	}

	/**
	 * Close the connection.
	 *
	 * @throws IOException in case of I/O errors
	 */
	void close() throws IOException {
		this.running = false;
		this.socket.close();
	}

}
