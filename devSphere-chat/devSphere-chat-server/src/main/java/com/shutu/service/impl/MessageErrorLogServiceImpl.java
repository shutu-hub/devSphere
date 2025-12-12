package com.shutu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shutu.mapper.MessageErrorLogMapper;
import com.shutu.model.entity.MessageErrorLog;
import com.shutu.service.MessageErrorLogService;
import org.springframework.stereotype.Service;

/**
 * 消息死信日志服务实现
 */
@Service
public class MessageErrorLogServiceImpl extends ServiceImpl<MessageErrorLogMapper, MessageErrorLog>
        implements MessageErrorLogService {
}