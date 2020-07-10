package kr.go.nanet.jcahn.redis.failover.thread;

enum WatchState {

	INIT,
	UP,
	GO_DOWN,
	DOWN,
	GO_UP
}