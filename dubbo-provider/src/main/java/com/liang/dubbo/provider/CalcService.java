package com.liang.dubbo.provider;

import com.liang.dubbo.api.CalcServiceApi;
import org.apache.dubbo.config.annotation.Service;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;


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
