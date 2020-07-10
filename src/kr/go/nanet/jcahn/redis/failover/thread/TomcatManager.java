package kr.go.nanet.jcahn.redis.failover.thread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TomcatManager {

	/**
	 * Redis 설정 태그 매쳐
	 */
	private static final String CONFIG_MATCHER = "org.redisson.tomcat.RedissonSessionManager";

	/**
	 * 톰캣 설정 파일 경로
	 */
	private static final String CONFIG_FILE_PATH = "/conf/context.xml";

	/**
	 * 톰캣 재기동 스크립트 파일 경로
	 */
	private static final String RESTART_SCRIPT_PATH = "/bin/wrapper.sh";

	/**
	 * 로거
	 */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 톰캣 기본 설치 경로
	 */
	private String tomcatBasePath;

	/**
	 * 백업 파일 날짜 포맷
	 */
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

	/**
	 * 생성자
	 * 
	 * @param prop 설정 정보
	 */
	TomcatManager(Properties prop) {

		tomcatBasePath = prop.getProperty("tomcat.basePath");
	}

	/**
	 * 톰캣의 Redis 설정 상태 코드를 반환한다.
	 * 
	 * @return Redis 설정 상태 코드.<br>
	 *         설정이 활성화된 상태인 경우에는 <b>ACTIVATE</b>를, 비활성화 상태인 경우에는 <b>DEACTIVATE</b>를 반환한다.
	 */
	Status checkTomcatRedisConfigStatus() {

		String configData = readConfigFile();

		int pivotIdx = configData.indexOf(CONFIG_MATCHER);
		int remarkBeginIdx = configData.lastIndexOf("<!--", pivotIdx);
		int remarkEndIdx = configData.lastIndexOf("-->", pivotIdx);

		return remarkBeginIdx > remarkEndIdx ? Status.DEACTIVATE : Status.ACTIVATE;
	}

	/**
	 * 톰캣 context.xml 파일의 내용을 읽어온다.
	 * 
	 * @return 파일 내용
	 */
	private String readConfigFile() {

		String result = null;

		File file = new File(tomcatBasePath + CONFIG_FILE_PATH);

		if (!file.exists() || !file.isFile() || file.length() < 1) {
			throw new RuntimeException("아래의 위치에 톰캣 설정 파일이 존재하지 않습니다.\n" + file.getAbsolutePath());
		}

		try (InputStream in = new FileInputStream(file)) {
			int fileLength = (int)file.length();

			byte[] buffer = new byte[fileLength];

			int readLength = in.read(buffer, 0, fileLength);

			if (readLength != fileLength) {
				throw new RuntimeException("읽어들인 톰캣 설정 파일의 정보와 실제 파일의 크기가 일치하지 않습니다.\n" + file.getAbsolutePath() + ", 읽음: " + readLength + "B, 파일 크기: " + fileLength + "B");
			}

			result = new String(buffer, "utf-8");
		} catch (IOException e) {
			logger.error(e.getMessage(), e);

			throw new RuntimeException(e);
		}

		return result;
	}

	/**
	 * Redis 설정 상태를 변경한다.
	 * @param status 변경하고자 하는 상태 코드.<br>
	 *        <code>Status.ACTIVATE</code>인 경우 설정을 활성화 상태로 변경하고 <code>Status.DEACTIVATE</code>인 경우 설정을 비활성화 상태로 변경한다.
	 */
	void changeConfigStatus(Status status) {

		Status currentStatus = checkTomcatRedisConfigStatus();

		if (status == currentStatus) {
			return;
		}

		if (status == Status.ACTIVATE) {
			configActivate();
		} else {
			configDeactivate();
		}
	}

	/**
	 * Redis 설정을 활성화 상태로 변경한다.
	 */
	private void configActivate() {

		String result = null;

		String configData = readConfigFile();

		int pivotIdx = configData.indexOf(CONFIG_MATCHER);
		int remarkBeginIdx = configData.lastIndexOf("<!--", pivotIdx);
		int remarkEndIdx = configData.indexOf("-->", pivotIdx);

		for (int idx = remarkBeginIdx + 4; idx < remarkEndIdx; idx ++) {
			if (configData.charAt(idx) == '<') {
				result = configData.substring(0, remarkBeginIdx) + configData.substring(idx, remarkEndIdx);
				break;
			} else if (configData.charAt(idx) == CONFIG_MATCHER.charAt(0)) {
				result = configData.substring(0, remarkBeginIdx + 1) + configData.substring(remarkBeginIdx + 4, remarkEndIdx);
				break;
			}
		}

		if (result == null) {
			throw new RuntimeException("톰캣 설정 파일 파싱에 실패하였습니다.\n" + configData);
		}

		for (int idx = remarkEndIdx - 1; idx >= remarkBeginIdx + 4; idx --) {
			if (configData.charAt(idx) == '>') {
				result += configData.substring(remarkEndIdx + 3);
				break;
			} else if (configData.charAt(idx) != '\r' && configData.charAt(idx) != '\n' && configData.charAt(idx) != '\t' && configData.charAt(idx) != ' ') {
				result += configData.substring(remarkEndIdx + 2);
				break;
			}
		}

		if (result == null || result.startsWith("null")) {
			throw new RuntimeException("톰캣 설정 파일 파싱에 실패하였습니다.\n" + configData);
		}

		backupConfigFile();

		createConfigFile(result);

		logger.debug("톰캣 설정을 활성화 상태로 변경하였습니다.");
	}

	/**
	 * 설정 파일의 백업을 생성한다.<br>
	 * <br>
	 * 기존 설정 파일명의 뒤에 '_연월일시분초' 정보를 더한 이름으로 변경한다.
	 */
	private void backupConfigFile() {

		File orgFile = new File(tomcatBasePath + CONFIG_FILE_PATH);
		File backupFile = new File(tomcatBasePath + CONFIG_FILE_PATH + "_" + dateFormat.format(new Date()));

		try (
			InputStream in = new FileInputStream(orgFile);
			OutputStream out = new FileOutputStream(backupFile);
		) {
			byte[] buffer = new byte[(int)orgFile.length()];

			in.read(buffer, 0, buffer.length);

			out.write(buffer, 0, buffer.length);
		} catch (IOException e) {
			throw new RuntimeException("톰캣 설정 파일의 백업 생성에 실패하였습니다.", e);
		}

		logger.debug("톰캣 설정 파일의 백업 파일을 생성했습니다. (파일명: " + backupFile.getName() + ")");
	}

	/**
	 * 주어진 정보로 설정 파일을 생성한다.
	 * 
	 * @param data 설정 파일 내용
	 */
	private void createConfigFile(String data) {

		File file = new File(tomcatBasePath + CONFIG_FILE_PATH);

		try (OutputStream out = new FileOutputStream(file)) {
			byte[] buffer = data.getBytes();

			out.write(buffer, 0, buffer.length);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);

			throw new RuntimeException(e);
		}

		logger.debug("톰캣 설정 파일을 생성했습니다.");
	}

	/**
	 * Redis 설정을 비활성화 상태로 변경한다.
	 */
	private void configDeactivate() {

		String result = null;

		String configData = readConfigFile();

		int pivotIdx = configData.indexOf(CONFIG_MATCHER);
		int remarkBeginIdx = configData.lastIndexOf("<", pivotIdx);
		int remarkEndIdx = configData.indexOf(">", pivotIdx);

		result = configData.substring(0, remarkBeginIdx + 1) + "!--" + configData.substring(remarkBeginIdx + 1, remarkEndIdx) + "--" + configData.substring(remarkEndIdx);

		backupConfigFile();

		createConfigFile(result);

		logger.debug("톰캣 설정을 비활성화 상태로 변경하였습니다.");
	}

	/**
	 * 톰캣 서버를 재기동한다.
	 */
	void restartTomcat() {

		logger.info("톰캣 재기동을 위해 " + tomcatBasePath + RESTART_SCRIPT_PATH + " 스크립트를 호출합니다...");

		Process process = null;

		try {
			process = Runtime.getRuntime().exec(tomcatBasePath + RESTART_SCRIPT_PATH);

			process.waitFor();
		} catch (IOException e) {
			logger.error("톰캣 서버 재기동 요청 과정에서 오류가 발생하였습니다.", e);

			throw new RuntimeException(e);
		} catch (InterruptedException e) {
		} finally {
			if (process != null) {
				process.destroy();
			}
		}

		logger.info("톰캣 서버 재기동을 요청하였습니다.");
	}
}