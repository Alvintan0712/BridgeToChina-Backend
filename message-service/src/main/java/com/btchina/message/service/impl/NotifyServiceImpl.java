package com.btchina.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.btchina.core.api.PageResult;
import com.btchina.core.api.ResultCode;
import com.btchina.core.exception.GlobalException;
import com.btchina.feign.clients.UserClient;
import com.btchina.message.constant.NotifyConstant;
import com.btchina.message.entity.Notify;
import com.btchina.message.mapper.NotifyMapper;
import com.btchina.message.model.form.NotifyQueryForm;
import com.btchina.message.model.vo.NotifyVO;
import com.btchina.model.enums.ActionEnum;
import com.btchina.model.enums.ObjectEnum;
import com.btchina.model.form.message.NotifyAddForm;
import com.btchina.message.service.NotifyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.btchina.model.vo.user.UserVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 消息通知表 服务实现类
 * </p>
 *
 * @author franky
 * @since 2023-03-30
 */
@Service
public class NotifyServiceImpl extends ServiceImpl<NotifyMapper, Notify> implements NotifyService {


    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private UserClient userClient;

    @Override
    public Boolean add(NotifyAddForm notifyAddForm) {
        Notify notify = new Notify();
        BeanUtils.copyProperties(notifyAddForm, notify);
        // 查询数据是否存在
        LambdaQueryWrapper<Notify> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Notify::getReceiverId, notifyAddForm.getReceiverId());
        queryWrapper.eq(Notify::getObjectType, notifyAddForm.getObjectType());
        queryWrapper.eq(Notify::getObjectId, notifyAddForm.getObjectId());
        Notify notifyDb = this.getOne(queryWrapper);
        // 如果存在则不保存
        if (notifyDb == null) {
            return false;
        }
        // 保存到数据库
        Boolean isSuccess = this.save(notify);
        if (isSuccess) {
            // 发送到消息队列
            rabbitTemplate.convertAndSend(NotifyConstant.EXCHANGE_NAME, NotifyConstant.PUSH_KEY, notify);
        }

        return isSuccess;
    }

    @Override
    public PageResult<NotifyVO> list(Long userId, NotifyQueryForm notifyQueryForm) {
        if (userId == null) {
            throw GlobalException.from(ResultCode.UNAUTHORIZED);
        }
        LambdaQueryWrapper<Notify> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Notify::getReceiverId, userId);
        if (notifyQueryForm.getIsRead() != null) {
            queryWrapper.eq(Notify::getIsRead, notifyQueryForm.getIsRead());
        }
        if (notifyQueryForm.getChannelType() != null) {
            queryWrapper.eq(Notify::getChannelType, notifyQueryForm.getChannelType());
        }

        Page<Notify> page = new Page<>(notifyQueryForm.getCurrentPage(), notifyQueryForm.getPageSize());
        Page<Notify> notifyPage = this.page(page, queryWrapper);
        PageResult<NotifyVO> pageResult = new PageResult<>();
        List<Notify> notifyList = notifyPage.getRecords();
        List<NotifyVO> notifyVOList = new ArrayList<>();
        //获取用户信息
        List<Long> userIds = new ArrayList<>();
        for (Notify notify : notifyList) {
            userIds.add(notify.getSenderId());
        }
        Map<Long, UserVO> userVOMap = userClient.findByIds(userIds);

        // 转换为VO
        for (Notify notify : notifyList) {
            NotifyVO notifyVO = new NotifyVO();
            BeanUtils.copyProperties(notify, notifyVO);
            notifyVO.setSenderName(userVOMap.get(notify.getSenderId()).getNickname());
            notifyVO.setSenderAvatar(userVOMap.get(notify.getSenderId()).getAvatar());
            notifyVO.setContent(convertNotifyContent(notifyVO));
            notifyVOList.add(notifyVO);
        }
        // 设置分页信息
        pageResult.setTotal(notifyPage.getTotal());
        pageResult.setList(notifyVOList);
        pageResult.setTotalPage((int) notifyPage.getPages());
        pageResult.setCurrentPage(notifyQueryForm.getCurrentPage());
        pageResult.setPageSize(notifyQueryForm.getPageSize());
        return pageResult;

    }


    public String convertNotifyContent(NotifyVO notify) {
        String content = "";
        switch (ObjectEnum.geObjectEnum(notify.getObjectType())) {
            case QUESTION:
                switch (ActionEnum.getActionEnum(notify.getActionType())) {
                    case ANSWER:
                        content = notify.getSenderName() + "回答了你的问题";
                        break;
                    case FAVORITE:
                        content = notify.getSenderName() + "收藏了你的问题";
                        break;
                    case LIKE:
                        content = notify.getSenderName() + "赞了你的问题";
                        break;
                    default:
                        break;
                }
                break;
            case USER:
                switch (ActionEnum.getActionEnum(notify.getActionType())) {
                    case FOLLOW:
                        content = notify.getSenderName() + "关注了你";
                        break;
                    default:
                        break;
                }
                break;
        }
        return content;
    }
}