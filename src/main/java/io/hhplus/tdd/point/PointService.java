package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PointService {

    private static final long MAX_BALANCE = 1_000_000_000L;

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final Lock lock = new ReentrantLock();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    public UserPoint getPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    public List<PointHistory> getHistories(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    public UserPoint charge(long userId, long amount) {
        lock.lock();
        try {
            UserPoint current = userPointTable.selectById(userId);
            long newAmount = current.point() + amount;

            if (newAmount > MAX_BALANCE) {
                throw new IllegalArgumentException("최대 잔고를 초과할 수 없습니다");
            }

            UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updated.updateMillis());
            return updated;
        } finally {
            lock.unlock();
        }
    }

    public UserPoint use(long userId, long amount) {
        lock.lock();
        try {
            UserPoint current = userPointTable.selectById(userId);

            if (current.point() < amount) {
                throw new IllegalArgumentException("잔액이 부족합니다");
            }

            long newAmount = current.point() - amount;
            UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.USE, updated.updateMillis());
            return updated;
        } finally {
            lock.unlock();
        }
    }
}
