package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/point")
public class PointControllerTest {

    private static final Logger log = LoggerFactory.getLogger(PointControllerTest.class);

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ReentrantLock lock = new ReentrantLock();
    private static final long MAX_BALANCE = 1_000_000_000L;

    public PointControllerTest(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    @GetMapping("{id}")
    public UserPoint point(@PathVariable long id) {
        return userPointTable.selectById(id);
    }

    @GetMapping("{id}/histories")
    public List<PointHistory> history(@PathVariable long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    @PatchMapping("{id}/charge")
    public UserPoint charge(@PathVariable long id, @RequestBody long amount) {
        lock.lock();
        try {
            UserPoint current = userPointTable.selectById(id);
            long updated = current.point() + amount;
            if (updated > MAX_BALANCE) {
                throw new IllegalArgumentException("최대 잔고를 초과할 수 없습니다");
            }
            UserPoint result = userPointTable.insertOrUpdate(id, updated);
            pointHistoryTable.insert(id, amount, TransactionType.CHARGE, result.updateMillis());
            return result;
        } finally {
            lock.unlock();
        }
    }

    @PatchMapping("{id}/use")
    public UserPoint use(@PathVariable long id, @RequestBody long amount) {
        lock.lock();
        try {
            UserPoint current = userPointTable.selectById(id);
            if (current.point() < amount) {
                throw new IllegalArgumentException("잔액이 부족합니다");
            }
            long updated = current.point() - amount;
            UserPoint result = userPointTable.insertOrUpdate(id, updated);
            pointHistoryTable.insert(id, amount, TransactionType.USE, result.updateMillis());
            return result;
        } finally {
            lock.unlock();
        }
    }
}
