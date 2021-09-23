### 项目说明

本项目目的是使用dubbo的filter



本项目使用了springboot+dubbo2。首先创建api，consumer和provider三个子模块



先定义api

```
public interface CalcServiceApi {
    String methodA();
    String methodB();
    String methodC();
}
```



创建provider中实现类，每个方法中随机休眠100ms，模拟请求过程中的延迟

```
@Service
public class CalcService implements CalcServiceApi {
    AtomicInteger atomicInteger = new AtomicInteger();

    @Override
    public String methodA() {
        try {
            Thread.sleep(new Random().nextInt(100));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int count = atomicInteger.getAndIncrement();
        System.out.println("执行第："+count+"次");
        return "a";
    }

    @Override
    public String methodB() {
        try {
            Thread.sleep(new Random().nextInt(100));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int count = atomicInteger.getAndIncrement();
        System.out.println("执行第："+count+"次");
        return "b";
    }

    @Override
    public String methodC() {
        try {
            Thread.sleep(new Random().nextInt(100));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int count = atomicInteger.getAndIncrement();
        System.out.println("执行第："+count+"次");
        return "c";
    }
}

```



然后添加消费模块

```
@RestController
public class CalcController {

		//注入远程api的接口
    @Reference
    private CalcServiceApi calcServiceApi;

    String[] methods = new String[]{"methodA","methodB","methodC"};

    @RequestMapping("/calc")
    public void calc(){

        //多线程去调用后面的接口
        ExecutorService executorService = Executors.newCachedThreadPool();

        while (true){
            int index = new Random().nextInt(3);
            executorService.submit(new Task(calcServiceApi,methods[index]));

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}

class Task implements Runnable{

    private CalcServiceApi calcServiceApi;

    private String methodName;

    public Task(CalcServiceApi calcServiceApi, String methodName) {
        this.calcServiceApi = calcServiceApi;
        this.methodName = methodName;
    }

    public void setCalcServiceApi(CalcServiceApi calcServiceApi) {
        this.calcServiceApi = calcServiceApi;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public void run() {

        try {
            Method declaredMethod = calcServiceApi.getClass().getDeclaredMethod(methodName, null);

            Object result = declaredMethod.invoke(calcServiceApi, null);

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
```

方法里面模拟高并发，使用线程池进行不停的请求，当访问localhost:8066/calc 时候，线程池开始启动，不停的发送请求，防止请求太快内存爆满导致机器挂掉，每次线程启动之前休息50ms。



上面都是普通的集成使用，下面开始做filter

```
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
                System.out.println(entry.getKey() + "() -- TP90：" + spentTimeList.get((int) Math.ceil(0.9d * size)).getTime() + "毫秒 -- TP99：" + spentTimeList.get((int) Math.ceil(0.99d * size)).getTime() + "毫秒, 当前有效个数：" + size);
            }
        }, 5, 5, TimeUnit.SECONDS);  // 初始延迟5秒之后开始每5秒执行一次
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
```

定义好filter之后，在resources/META-INFO/dubbo中创建名称为org.apache.dubbo.rpc.Filter的文件，其中以key-value的方式放入tpFilter=com.liang.consumer.TPMonitorFilter，这样dubbo就会解析到并加载。



启动之前先进入到项目中，修改application.yml中的zookeeper的相关信息，然后启动之后就可以看到效果

```
2021-09-23 21:23:53
methodA() -- TP90是：94毫秒 -- TP99是：106毫秒, 可用个数：132
methodB() -- TP90是：98毫秒 -- TP99是：132毫秒, 可用个数：134
methodC() -- TP90是：98毫秒 -- TP99是：106毫秒, 可用个数：141
2021-09-23 21:23:58
methodA() -- TP90是：93毫秒 -- TP99是：106毫秒, 可用个数：163
methodB() -- TP90是：98毫秒 -- TP99是：132毫秒, 可用个数：163
methodC() -- TP90是：98毫秒 -- TP99是：106毫秒, 可用个数：174
2021-09-23 21:24:03
methodA() -- TP90是：94毫秒 -- TP99是：106毫秒, 可用个数：196
methodB() -- TP90是：98毫秒 -- TP99是：132毫秒, 可用个数：188
methodC() -- TP90是：98毫秒 -- TP99是：106毫秒, 可用个数：212
```

