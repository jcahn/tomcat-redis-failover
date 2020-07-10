package kr.go.nanet.jcahn.redis.failover.thread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RedisStatusChecker {

	private static String CHECK_COMMAND = "quit\n";
	private static String EXPECTED_RESPONSE = "+OK";

	/**
	 * 로거
	 */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 감시 대상 Redis 서버 정보
	 */
	private String redisHost;
	private int redisPort;

	/**
	 * 생성자
	 * 
	 * @param prop 설정 정보
	 */
	RedisStatusChecker(Properties prop) {

		redisHost = prop.getProperty("redis.host");
		redisPort = Integer.parseInt(prop.getProperty("redis.port"));
	}

	/**
	 * Redis 서버의 구동 상태를 반환한다.
	 * 
	 * @return 구동 상태 코드.<br>
	 *         서버가 구동 중이면 <code>Status.ACTIVATE</code>를, 구동 중이 아니면 <code>Status.DEACTIVATE</code>를 반환한다.
	 */
	Status checkRedisServerStatus() {

		byte[] response = new byte[3];

		try (
			Socket socket = new Socket(redisHost, redisPort);
			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();
		) {
			out.write(CHECK_COMMAND.getBytes());

			out.flush();

			in.read(response, 0, 3);
		} catch (IOException e) {
			logger.debug("Redis 서버와의 통신에 실패하였습니다.");

			return Status.DEACTIVATE;
		}

		if (new String(response).equals(EXPECTED_RESPONSE)) {
			logger.debug("Redis 서버와의 통신에 성공하였습니다.");

			return Status.ACTIVATE;
		} else {
			logger.debug("Redis 서버와 연결은 되나 응답이 적절하지 않습니다.");

			return Status.DEACTIVATE;
		}
	}
}