package com.virtuallover.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.virtuallover.dao.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
