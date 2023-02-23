package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // registry 是拦截器的注册器
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns( //除了以下路径
                "/shop/**",
                "/shop-type/**",
                "/voucher/**",
                "/upload/**",
                "/user/code",
                "/user/login",
                "/blog/hot"
        ).order(1);// 添加拦截器,并放行不需要拦截的路径

        // 刷新token的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);//包括所有路径
    }
}
