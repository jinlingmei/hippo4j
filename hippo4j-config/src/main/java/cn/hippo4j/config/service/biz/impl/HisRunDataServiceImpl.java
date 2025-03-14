/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hippo4j.config.service.biz.impl;

import cn.hippo4j.common.monitor.Message;
import cn.hippo4j.common.monitor.MessageWrapper;
import cn.hippo4j.common.monitor.RuntimeMessage;
import cn.hippo4j.common.toolkit.GroupKey;
import cn.hippo4j.common.toolkit.MessageConvert;
import cn.hippo4j.common.web.base.Result;
import cn.hippo4j.common.web.base.Results;
import cn.hippo4j.config.config.ServerBootstrapProperties;
import cn.hippo4j.config.mapper.HisRunDataMapper;
import cn.hippo4j.config.model.HisRunDataInfo;
import cn.hippo4j.config.model.biz.monitor.MonitorActiveRespDTO;
import cn.hippo4j.config.model.biz.monitor.MonitorQueryReqDTO;
import cn.hippo4j.config.model.biz.monitor.MonitorRespDTO;
import cn.hippo4j.config.monitor.QueryMonitorExecuteChoose;
import cn.hippo4j.config.service.biz.HisRunDataService;
import cn.hippo4j.config.toolkit.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.hutool.core.date.DatePattern.NORM_TIME_PATTERN;

/**
 * His run data service impl.
 */
@Service
@AllArgsConstructor
public class HisRunDataServiceImpl extends ServiceImpl<HisRunDataMapper, HisRunDataInfo> implements HisRunDataService {

    private final ServerBootstrapProperties properties;

    private final QueryMonitorExecuteChoose queryMonitorExecuteChoose;

    private final ThreadPoolTaskExecutor monitorThreadPoolTaskExecutor;

    @Override
    public List<MonitorRespDTO> query(MonitorQueryReqDTO reqDTO) {
        Date currentDate = new Date();
        DateTime dateTime = DateUtil.offsetMinute(currentDate, -properties.getCleanHistoryDataPeriod());
        long startTime = dateTime.getTime();
        List<HisRunDataInfo> hisRunDataInfos = this.lambdaQuery()
                .eq(HisRunDataInfo::getTenantId, reqDTO.getTenantId())
                .eq(HisRunDataInfo::getItemId, reqDTO.getItemId())
                .eq(HisRunDataInfo::getTpId, reqDTO.getTpId())
                .eq(HisRunDataInfo::getInstanceId, reqDTO.getInstanceId())
                .between(HisRunDataInfo::getTimestamp, startTime, currentDate.getTime())
                .orderByAsc(HisRunDataInfo::getTimestamp)
                .list();
        return BeanUtil.convert(hisRunDataInfos, MonitorRespDTO.class);
    }

    @Override
    public MonitorActiveRespDTO queryInfoThreadPoolMonitor(MonitorQueryReqDTO reqDTO) {
        Date currentDate = new Date();
        DateTime dateTime = DateUtil.offsetMinute(currentDate, -properties.getCleanHistoryDataPeriod());
        long startTime = dateTime.getTime();
        List<HisRunDataInfo> hisRunDataInfos = this.lambdaQuery()
                .eq(HisRunDataInfo::getTenantId, reqDTO.getTenantId())
                .eq(HisRunDataInfo::getItemId, reqDTO.getItemId())
                .eq(HisRunDataInfo::getTpId, reqDTO.getTpId())
                .eq(HisRunDataInfo::getInstanceId, reqDTO.getInstanceId())
                .between(HisRunDataInfo::getTimestamp, startTime, currentDate.getTime())
                .orderByAsc(HisRunDataInfo::getTimestamp)
                .list();
        List<String> times = Lists.newArrayList();
        List<Long> poolSizeList = Lists.newArrayList();
        List<Long> activeSizeList = Lists.newArrayList();
        List<Long> queueCapacityList = Lists.newArrayList();
        List<Long> queueSizeList = Lists.newArrayList();
        List<Long> completedTaskCountList = Lists.newArrayList();
        List<Long> rejectCountList = Lists.newArrayList();
        List<Long> queueRemainingCapacityList = Lists.newArrayList();
        List<Long> currentLoadList = Lists.newArrayList();
        long countTemp = 0L;
        AtomicBoolean firstFlag = new AtomicBoolean(Boolean.TRUE);
        for (HisRunDataInfo each : hisRunDataInfos) {
            String time = DateUtil.format(new Date(each.getTimestamp()), NORM_TIME_PATTERN);
            times.add(time);
            poolSizeList.add(each.getPoolSize());
            activeSizeList.add(each.getActiveSize());
            queueSizeList.add(each.getQueueSize());
            rejectCountList.add(each.getRejectCount());
            queueRemainingCapacityList.add(each.getQueueRemainingCapacity());
            currentLoadList.add(each.getCurrentLoad());
            queueCapacityList.add(each.getQueueCapacity());
            if (firstFlag.get()) {
                completedTaskCountList.add(0L);
                firstFlag.set(Boolean.FALSE);
                countTemp = each.getCompletedTaskCount();
                continue;
            }
            long completedTaskCount = each.getCompletedTaskCount();
            long countTask = completedTaskCount - countTemp;
            completedTaskCountList.add(countTask);
            countTemp = each.getCompletedTaskCount();
        }
        return new MonitorActiveRespDTO(times, poolSizeList, activeSizeList, queueSizeList, completedTaskCountList, rejectCountList, queueRemainingCapacityList, currentLoadList, queueCapacityList);
    }

    @Override
    public MonitorRespDTO queryThreadPoolLastTaskCount(MonitorQueryReqDTO reqDTO) {
        Date currentDate = new Date();
        DateTime dateTime = DateUtil.offsetMinute(currentDate, -properties.getCleanHistoryDataPeriod());
        long startTime = dateTime.getTime();
        HisRunDataInfo hisRunDataInfo = this.lambdaQuery()
                .eq(HisRunDataInfo::getTenantId, reqDTO.getTenantId())
                .eq(HisRunDataInfo::getItemId, reqDTO.getItemId())
                .eq(HisRunDataInfo::getTpId, reqDTO.getTpId())
                .eq(HisRunDataInfo::getInstanceId, reqDTO.getInstanceId())
                .orderByDesc(HisRunDataInfo::getTimestamp)
                .between(HisRunDataInfo::getTimestamp, startTime, currentDate.getTime())
                .last("LIMIT 1")
                .one();
        return BeanUtil.convert(hisRunDataInfo, MonitorRespDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Message message) {
        List<RuntimeMessage> runtimeMessages = message.getMessages();
        List<HisRunDataInfo> hisRunDataInfos = Lists.newArrayList();
        runtimeMessages.forEach(each -> {
            HisRunDataInfo hisRunDataInfo = BeanUtil.convert(each, HisRunDataInfo.class);
            String[] parseKey = GroupKey.parseKey(each.getGroupKey());
            hisRunDataInfo.setTpId(parseKey[0]);
            hisRunDataInfo.setItemId(parseKey[1]);
            hisRunDataInfo.setTenantId(parseKey[2]);
            hisRunDataInfo.setInstanceId(parseKey[3]);
            hisRunDataInfos.add(hisRunDataInfo);
        });
        this.saveBatch(hisRunDataInfos);
    }

    @Override
    public Result<Void> dataCollect(MessageWrapper messageWrapper) {
        Runnable task = () -> {
            Message message = MessageConvert.convert(messageWrapper);
            queryMonitorExecuteChoose.chooseAndExecute(message);
        };
        try {
            monitorThreadPoolTaskExecutor.execute(task);
        } catch (Exception ex) {
            log.error("Monitoring data insertion database task overflow.", ex);
        }
        return Results.success();
    }
}
