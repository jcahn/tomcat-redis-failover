package kr.go.nanet.jcahn.redis.failover.thread;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchDog extends Thread {

	/**
	 * 10 초(밀리 초 단위)
	 */
	private static long TEN_SECONDS = 10000;

	/**
	 * 로거
	 */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Redis 서버 상태 관리자
	 */
	private RedisStatusChecker redisChecker;

	/**
	 * 톰캣 관리자
	 */
	private TomcatManager tomcatManager;

	/**
	 * 상태 경고 발송 관리자
	 */
	private AlertManager alertManager;

	/**
	 * Redis 서버 동작 상태
	 */
	private WatchState state = WatchState.INIT;

	/**
	 * 생성자
	 * 
	 * @param prop 설정 정보
	 */
	public WatchDog(Properties prop) {

		super();

		setDaemon(true);

		redisChecker = new RedisStatusChecker(prop);
		tomcatManager = new TomcatManager(prop);
		alertManager = new AlertManager(prop);
	}

	/**
	 * 쓰레드 본문
	 */
	@Override
	public void run() {

		boolean runnable = true;

		logger.info("Redis 동작 감시기가 구동되었습니다.");

		try {
			while (runnable && !isInterrupted()) {
				watch();

				try {
					sleep(TEN_SECONDS);
				} catch (InterruptedException e) {
					logger.debug("Redis 동작 감시기 종료 인터럽트가 수신되었습니다.");

					runnable = false;
				}
			}
		} catch (Exception e) {
			logger.error("Redis 동작 감시기 구동 중 오류가 발생하여 강제 종료됩니다.", e);
		}

		logger.info("Redis 동작 감시기가 종료되었습니다.");
	}

	/**
	 * Redis 서버 동작을 감시하고 상태에 따른 작업을 수행한다.<br>
	 * <br>
	 * 감시 서비스는 유한 상태 머신 형태로 구현되어 있으며, 아래의 상태 중 하나를 가진다.<br>
	 * <br>
	 * <b>INIT</b> - 최초 구동 상태.<br>
	 * <b>UP</b> - 정상 동작 상태.<br>
	 * <b>GO_DOWN</b> - 정상 동작 중 정지된 상태.<br>
	 * <b>DOWN</b> - 정지 상태.<br>
	 * <b>GO_UP</b> - 정지 상태에서 정상 동작으로 변경된 상태.
	 */
	private void watch() {

		if (state == WatchState.INIT) {
			processInitState();
		} else if (state == WatchState.UP) {
			processUpState();
		} else if (state == WatchState.DOWN) {
			processDownState();
		} else if (state == WatchState.GO_UP) {
			processGoUpState();
		} else if (state == WatchState.GO_DOWN) {
			processGoDownState();
		}
	}

	/**
	 * <b>INIT</b> 상태의 동작을 수행한다.<br>
	 * <br>
	 * 먼저 톰캣의 Redis 설정과 Redis 서버의 상태를 모두 확인하여 서버는 활성화, 설정은 비활성화 상태인 경우 (1)톰캣의 Redis 설정 활성화, (2)톰캣 리스타트, <b>GO_UP</b> 상태로 전이한다. 선행 조건 불충족 시 설정의 활성화 상태를 확인하여 <b>UP</b> 또는 <b>DOWN</b> 상태로 전이한다.
	 */
	private void processInitState() {

		Status serverStatus = redisChecker.checkRedisServerStatus();

		logger.debug("Redis 서버 상태: " + serverStatus);

		Status configStatus = tomcatManager.checkTomcatRedisConfigStatus();

		logger.debug("톰캣 설정 상태: " + configStatus);

		if (serverStatus == Status.ACTIVATE && configStatus == Status.DEACTIVATE) {
			logger.info("Redis 서버 상태와 톰캣 설정 상태가 일치하지 않습니다.");

			tomcatManager.changeConfigStatus(Status.ACTIVATE);

			tomcatManager.restartTomcat();

			state = WatchState.GO_UP;

			logger.info("감시 서비스의 상태가 INIT에서 GO_UP으로 전이됩니다.");

			return;
		}

		if (configStatus == Status.ACTIVATE) {
			state = WatchState.UP;

			logger.info("감시 서비스의 상태가 INIT에서 UP으로 전이됩니다.");
		} else {
			state = WatchState.DOWN;

			logger.info("감시 서비스의 상태가 INIT에서 DOWN으로 전이됩니다.");
		}
	}

	/**
	 * <b>UP</b> 상태의 동작을 수행한다.<br>
	 * <br>
	 * Redis 서버가 비활성화 상태인 경우 (1)톰캣의 Redis 설정 비활성화, (2)톰캣 리스타트, (3)관리자에게 경고 발송, (4)<b>GO_DOWN</b> 상태로 전이한다.
	 */
	private void processUpState() {

		Status status = redisChecker.checkRedisServerStatus();

		logger.debug("Redis 서버 상태: " + status);

		if (status == Status.ACTIVATE) {
			return;
		}

		logger.info("Redis 서버에 장애가 발생하였습니다.");

		tomcatManager.changeConfigStatus(Status.DEACTIVATE);

		alertManager.sendAlertMail();

		tomcatManager.restartTomcat();

		state = WatchState.GO_DOWN;

		logger.info("감시 서비스의 상태가 UP에서 GO_DOWN으로 전이됩니다.");
	}

	/**
	 * <b>DOWN</b> 상태의 동작을 수행한다.<br>
	 * <br>
	 * Redis 서버가 활성화 상태인 경우 (1)톰캣의 Redis 설정 활성화, (2)<b>GO_UP</b> 상태로 전이한다.
	 */
	private void processDownState() {

		Status status = redisChecker.checkRedisServerStatus();

		logger.debug("Redis 서버 상태: " + status);

		if (status == Status.DEACTIVATE) {
			return;
		}

		logger.info("Redis 서버가 정상화되었습니다.");

		tomcatManager.changeConfigStatus(Status.ACTIVATE);

		state = WatchState.GO_UP;

		logger.info("감시 서비스의 상태가 DOWN에서 GO_UP으로 전이됩니다.");
	}

	/**
	 * <b>GO_UP</b> 상태의 동작을 수행한다.<br>
	 * <br>
	 * Redis 서버가 비활성화 상태인 경우 (1)톰캣의 Redis 설정 비활성화, (2)<b>GO_DOWN</b> 상태로 전이한다.
	 */
	private void processGoUpState() {

		Status status = redisChecker.checkRedisServerStatus();

		logger.debug("Redis 서버 상태: " + status);

		if (status == Status.ACTIVATE) {
			return;
		}

		logger.info("Redis 서버에 장애가 발생하였습니다.");

		tomcatManager.changeConfigStatus(Status.DEACTIVATE);

		state = WatchState.GO_DOWN;

		logger.info("감시 서비스의 상태가 GO_UP에서 GO_DOWN으로 전이됩니다.");
	}

	/**
	 * <b>GO_DOWN</b> 상태의 동작을 수행한다.<br>
	 * <br>
	 * Redis 서버가 활성화 상태인 경우 (1)톰캣의 Redis 설정 활성화, (2)<b>GO_UP</b> 상태로 전이한다.
	 */
	private void processGoDownState() {

		Status status = redisChecker.checkRedisServerStatus();

		logger.debug("Redis 서버 상태: " + status);

		if (status == Status.DEACTIVATE) {
			return;
		}

		logger.info("Redis 서버가 정상화되었습니다.");

		tomcatManager.changeConfigStatus(Status.ACTIVATE);

		state = WatchState.GO_UP;

		logger.info("감시 서비스의 상태가 GO_DOWN에서 GO_UP으로 전이됩니다.");
	}
}