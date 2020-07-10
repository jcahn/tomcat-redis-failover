# tomcat-redis-failover
Tomcat-redisson-Redis 연동 환경에서 redis에 장애 발생 시 tomcat에 발생하는 장애를 회피하도록 하여 서비스 중단 시간을 최소화하는 redis 동작 감시 라이브러리입니다.

## 의존성
아래의 라이브러리가 필요합니다.

* Spring framework
* Java Mail
* SLF4J

## 사용법

(1) tomcat-redis-failover.jar 파일을 /WEB-INF/lib 디렉토리에 넣습니다.

(2) 아래의 내용을 spring 설정 파일이 있는 디렉토리에 넣습니다. (beans 태그의 xsi:schemaLocation 설정 부분에 현재 사용하고 있는 spring 프레임워크의 버전을 확인하여 xsd 파일 정보를 맞게 변경하시기 바랍니다.) 

> 파일명: context-redis-watcher.xml

    <?xml version="1.0" encoding="UTF-8"?>
    <beans xmlns="http://www.springframework.org/schema/beans"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xmlns:util="http://www.springframework.org/schema/util"
           xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.0.xsd http://www.springframework.org/schema/util http://www.springframework.org/schema/util/spring-util-4.0.xsd">
        <bean id="redisWatcher" class=" kr.go.nanet.jcahn.redis.failover.RedisWatcher" />
        <util:properties id="redisWatcherProperties" location="redis-watcher.properties 파일의 경로" />
    </beans>

(3) 아래의 내용을 properties 파일이 있는 디렉토리에 넣습니다.

> 파일명: redis-watcher.properties

    # Redis 서버 호스트명 또는 IP
    redis.host=127.0.0.1
    
    # Redis 서버 포트
    redis.port=6379
    
    # 톰캣 설치 기본 경로
    tomcat.basePath=톰캣 설치 경로
    
    # sendmail 서버 호스트 또는 IP
    alert.mailServerHost=127.0.0.1
    
    # sendmail 서버 포트
    alert.mailServerPort=25
    
    # 서버 장애 알림 메일 수신자 이메일 목록
    # 수신 대상자가 여러 명일 경우 각각을 공백 없이 콤마(,)로 구분하여 나열
    alert.email=admin@mydomain.com
    
    # 서버 장애 알림 메일 제목
    alert.mailTitle=REDIS 서버 장애 발생 알림
    
    # 서버 장애 알림 메일 내용
    alert.mailBody=<html><body><p>REDIS 서버에 장애가 발생하였습니다.</p><p>발생한 장애에 대비하기 위해 톰캣 서버의 REDIS 연동 설정을 비활성화하였습니다.</p><p>REDIS 서비스가 복구되면 톰캣 서버의 REDIS 연동 설정을 활성화한 후 재기동하셔야 합니다.</p><p>REDIS 서비스가 정상회된 후 톰캣의 REDIS 연동 설정을 활성화하지 않은 상태에서 톰캣을 재기동하면 자동으로 설정이 활성화되며 자동으로 한번 더 재기동됩니다.</p><p>연속 재기동이 불편한 경우에는 반드시 REDIS 연동 설정을 활성화한 후 재기동하시기 바랍니다.</p></body></html>

(4) 아래의 내용을 톰캣 /bin/ 디렉토리에 넣습니다. (스크립트 파일에 실행 권한을 주어야 합니다.)

> 파일명: wrapper.sh

    #!/bin/bash
    /톰캣 설치 경로/bin/restart.sh &

> 파일명: restart.sh

    #!/bin/bash
    export JAVA_HOME="자바 설치 경로"
    export PATH="자바 설치 경로"
    cd /톰캣 설치 경로/bin
    sleep 5
    ./shutdown.sh
    sleep 10
    ./startup.sh

(5) 톰캣 재기동 후 동작 상태를 확인합니다.
