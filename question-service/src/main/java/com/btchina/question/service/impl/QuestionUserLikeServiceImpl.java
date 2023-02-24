package com.btchina.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.btchina.core.api.ResultCode;
import com.btchina.core.exception.GlobalException;
import com.btchina.question.constant.QuestionConstant;
import com.btchina.question.entity.Question;
import com.btchina.question.entity.QuestionUserLike;
import com.btchina.question.mapper.QuestionUserLikeMapper;
import com.btchina.question.service.QuestionService;
import com.btchina.question.service.QuestionUserLikeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 问题点赞表 服务实现类
 * </p>
 *
 * @author franky
 * @since 2023-02-09
 */
@Service
public class QuestionUserLikeServiceImpl extends ServiceImpl<QuestionUserLikeMapper, QuestionUserLike> implements QuestionUserLikeService {


    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private QuestionService questionService;

    /**
     * 点赞
     *
     * @param questionId 问题id
     * @param userId     用户id
     * @return
     */
    @Override
    public Boolean like(Long questionId, Long userId) {
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw GlobalException.from(ResultCode.QUESTION_NOT_EXIST);
        }
        // 查询是否已经点赞
        QuestionUserLike questionUserLike = getByQuestionIdAndUserId(questionId, userId);
        // 曾经点赞过
        if (questionUserLike != null) {
            // 已经点赞
            if (questionUserLike.getStatus() == 1) {
                return true;
            }
            questionUserLike.setStatus(1);
            this.updateById(questionUserLike);
        } else {
            // 未点赞过
            QuestionUserLike userLike = new QuestionUserLike();
            userLike.setQuestionId(questionId);
            userLike.setUserId(userId);
            userLike.setStatus(1);
            this.save(userLike);
        }
        increaseLikeCount(question);
        return true;
    }


    @Override
    public Boolean unlike(Long questionId, Long userId) {
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw GlobalException.from(ResultCode.QUESTION_NOT_EXIST);
        }
        // 查询是否已经点赞
        QuestionUserLike questionUserLike = getByQuestionIdAndUserId(questionId, userId);
        // 曾经点赞过
        if (questionUserLike != null) {
            // 已经取消赞
            if (questionUserLike.getStatus() == 0) {
                return true;
            }
            questionUserLike.setStatus(0);
            this.updateById(questionUserLike);
        } else {
            // 未点赞过
            QuestionUserLike userUnlike = new QuestionUserLike();
            userUnlike.setQuestionId(questionId);
            userUnlike.setUserId(userId);
            userUnlike.setStatus(0);
            this.save(userUnlike);
        }
        decreaseLikeCount(question);
        return true;
    }

    @Override
    public QuestionUserLike getQuestionUserLike(Long questionId, Long userId) {
        if (questionId == null || userId == null) {
            return null;
        }
        return getByQuestionIdAndUserId(questionId, userId);
    }


    public QuestionUserLike getByQuestionIdAndUserId(Long questionId, Long userId) {
        LambdaQueryWrapper<QuestionUserLike> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QuestionUserLike::getQuestionId, questionId);
        queryWrapper.eq(QuestionUserLike::getUserId, userId);
        return this.getOne(queryWrapper);
    }

    /**
     * 增加点赞数
     * @param question
     */
    public void increaseLikeCount(Question  question) {
        if (question == null) {
            throw GlobalException.from("问题不存在");
        }
        question.setLikeCount(question.getLikeCount() + 1);
        Boolean isSuccess = questionService.updateById(question);
        if (isSuccess) {
            rabbitTemplate.convertAndSend(QuestionConstant.EXCHANGE_NAME, QuestionConstant.UPDATE_KEY, question);
        }
    }

    /**
     * 减少点赞数
     * @param question
     */
    public void decreaseLikeCount(Question  question) {
        if (question == null) {
            throw GlobalException.from("问题不存在");
        }
        question.setLikeCount(question.getLikeCount() -1);
        Boolean isSuccess = questionService.updateById(question);
        if (isSuccess) {
            rabbitTemplate.convertAndSend(QuestionConstant.EXCHANGE_NAME, QuestionConstant.UPDATE_KEY, question);
        }
    }
}
