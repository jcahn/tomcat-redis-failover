package kr.go.nanet.jcahn.redis.failover.thread;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AlertManager {

	/**
	 * 발송자 메일 주소
	 */
	private static final String SENDER_EMAIL = "watchdog@scholar.go.kr";

	/**
	 * 로거
	 */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 메일 발송 정보
	 */
	private Properties mailProp;

	/**
	 * 메일 발송 대상 이메일 목록
	 */
	private String[] emailList;

	/**
	 * 경고 메일 제목
	 */
	private String mailTitle;

	/**
	 * 발송 메일 본문
	 */
	private String mailBody;

	/**
	 * 생성저
	 * 
	 * @param prop 설정 정보
	 */
	AlertManager(Properties prop) {

		mailProp = new Properties();
		mailProp.put("mail.smtp.host", prop.getProperty("alert.mailServerHost"));
		mailProp.put("mail.smtp.port", prop.getProperty("alert.mailServerPort"));
		mailProp.put("mail.smtp.auth", "false");

		emailList = prop.getProperty("alert.email").split(",");
		mailTitle = prop.getProperty("alert.mailTitle");
		mailBody = prop.getProperty("alert.mailBody");
	}

	/**
	 * 경고 메일을 발송한다.
	 */
	public void sendAlertMail() {

		try {
			Session session = Session.getDefaultInstance(mailProp, null);

			MimeMessage message = new MimeMessage(session);

			message.setFrom(new InternetAddress(SENDER_EMAIL, "감시 서비스", "UTF-8"));
			message.setSubject(mailTitle);
			message.setContent(mailBody, "text/html; charset=UTF-8");

			for (String email : emailList) {
				message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));

				Transport.send(message);
			}

			logger.debug("관리자에게 장애 발생 경고 메일을 발송하였습니다.");
		} catch (Exception e) {
			logger.error("메일 발송 중 오류가 발생하였습니다.", e);
		}
	}
}