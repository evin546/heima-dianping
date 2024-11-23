package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData<T> {
    private T data;
    private LocalDateTime expireTime;
}
