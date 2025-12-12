package com.shutu.common.listener;

import cn.hutool.json.JSONUtil;
import com.shutu.model.entity.MessageErrorLog;
import com.shutu.service.MessageErrorLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 死信队列消费者
 * 职责：
 * 1. 将无法处理的消息持久化到 MySQL 的错误日志表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqMessageListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final MessageErrorLogService messageErrorLogService;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        Map<String, String> value = record.getValue();
        String tempId = value.get("tempId");
        log.error("DLQ 收到死信消息，准备落库告警: tempId={}", tempId);

        try {
            // 1. 转换为实体
            MessageErrorLog errorLog = new MessageErrorLog();
            errorLog.setTempId(tempId);
            errorLog.setFromUid(Long.valueOf(value.get("fromUserId")));
            errorLog.setTargetId(Long.valueOf(value.get("targetId")));
            errorLog.setContent(value.get("content"));
            errorLog.setType(Integer.parseInt(value.get("type")));
            errorLog.setOriginalPayload(JSONUtil.toJsonStr(value)); // 保存原始 JSON 以便排查
            errorLog.setErrorMessage("Retry count exceeded limit"); // 错误原因
            errorLog.setStatus(0); // 0-待处理，1-已人工修复

            // 2. 保存到 MySQL
            messageErrorLogService.save(errorLog);

            log.info("死信消息已归档: id={}", errorLog.getId());

        } catch (Exception e) {
            // 如果连死信处理都报错了，那就真的只能打日志了
            log.error("DLQ 严重错误：死信落库失败！！！数据可能丢失。Raw: {}", value, e);
        }
    }
}