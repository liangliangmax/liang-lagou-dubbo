package com.liang.consumer;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.rpc.*;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

@Activate(group = {CommonConstants.CONSUMER}) // 标明在consumer端启用
public class TPMonitorFilter implements Filter {

    /*存放每个方法的响应时间数据*/
    private final Map<String, DelayQueue<SpentTime>> mappedMethodSpentTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    {
        System.out.println("----开始执行filter----");
        // 启动定时任务
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            for (Map.Entry<String, DelayQueue<SpentTime>> entry : mappedMethodSpentTime.entrySet()) {
                final DelayQueue<SpentTime> delayQueue = entry.getValue();
                // 移除已经过期的数据
                delayQueue.removeIf(new Predicate<SpentTime>() {
                    @Override
                    public boolean test(SpentTime spentTime) {
                        return spentTime.getDelay(TimeUnit.MILLISECONDS) <= 0;
                    }
                });
                // 取出数据转为数组
                final SpentTime[] spentTimes = delayQueue.toArray(new SpentTime[]{});
                // 转为List
                final List<SpentTime> spentTimeList = Arrays.asList(spentTimes);
                // 排序
                Collections.sort(spentTimeList, (o1, o2) -> (int) (o1.getTime() - o2.getTime()));
                final int size = spentTimeList.size();
                System.out.println(entry.getKey() + "() -- TP90是：" + spentTimeList.get((int) Math.ceil(0.9d * size)).getTime() + "毫秒 -- TP99是：" + spentTimeList.get((int) Math.ceil(0.99d * size)).getTime() + "毫秒, 可用个数：" + size);
            }
        }, 30, 5, TimeUnit.SECONDS);  // 初始延迟5秒之后开始每5秒执行一次
    }



    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        long startTime = System.currentTimeMillis();
        Result invoke = null;
        try {
            invoke = invoker.invoke(invocation);
        } catch (RpcException e) {
            e.printStackTrace();
        } finally {
            final long spentTime = System.currentTimeMillis() - startTime;
            final String methodName = invocation.getMethodName();
            final String key =  methodName;
            DelayQueue<SpentTime> delayQueue = mappedMethodSpentTime.get(key);
            if (delayQueue == null) {
                delayQueue = new DelayQueue<>();
            }
            // 记录方法响应时间，过期时间60秒
            delayQueue.put(new SpentTime(spentTime, 60 * 1000));
            mappedMethodSpentTime.put(key, delayQueue);
        }
        return invoke;

    }


}

class SpentTime implements Delayed {
    /* 响应时长 毫秒*/
    private final long time;
    /* 过期时间 */
    private final long expire;

    SpentTime(long time, long delay) {
        this.time = time;
        this.expire = System.currentTimeMillis() + delay;
    }

    public long getTime() {
        return time;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(this.expire - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        final SpentTime o1 = (SpentTime) o;
        return (int) (this.getTime() - o1.getTime());
    }

}
