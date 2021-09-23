package com.liang.consumer;

import com.liang.dubbo.api.CalcServiceApi;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class CalcController {

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
