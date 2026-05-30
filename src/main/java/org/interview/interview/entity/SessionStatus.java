package org.interview.interview.entity;

public enum SessionStatus {
    CREATED,//已创建，刚生成会话
    IN_PROGRESS,//进行中，面试官正在提问，候选人正在回答
    COMPLETED,//已完成，面试结束，等待评估结果
    EVALUATED//已评估，面试官已提交评估结果
}
