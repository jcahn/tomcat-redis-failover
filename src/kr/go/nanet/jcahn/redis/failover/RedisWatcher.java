package kr.go.nanet.jcahn.redis.failover;

import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import kr.go.nanet.jcahn.redis.failover.thread.WatchDog;

public class RedisWatcher implements ApplicationContextAware {

	/**
	 * 로거
	 */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Redis 동작 감시기 쓰레드
	 */
	private Thread watchDog;

	/**
	 * Spring application context
	 */
	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

		this.applicationContext = applicationContext;
	}

	/**
	 * 생성자
	 */
	@PostConstruct
	public void startup() {

		logger.info("Redis 동작 감시기 구동을 시작합니다...");

		Properties prop = (Properties)applicationContext.getBean("redisWatcherProperties");

		watchDog = new WatchDog(prop);

		watchDog.start();
	}

	/**
	 * 소멸자
	 */
	@PreDestroy
	public void shutdown() {

		if (watchDog == null) {
			logger.info("Redis 동작 감시기가 생성되어 있지 않습니다.");

			return;
		}

		if (!watchDog.isAlive()) {
			logger.info("Redis 동작 감시기가 이미 종료되었습니다.");

			return;
		}

		logger.info("Redis 동작 감시기를 종료합니다...");

		watchDog.interrupt();
	}
}