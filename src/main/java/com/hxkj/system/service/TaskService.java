package com.hxkj.system.service;

import com.hxkj.common.constant.TaskConstant;
import com.hxkj.common.exception.ErrorMsgException;
import com.hxkj.common.task.TaskWraper;
import com.hxkj.common.util.ToolDateTime;
import com.hxkj.system.model.SysTask;
import com.jfinal.aop.Before;
import com.jfinal.plugin.activerecord.tx.Tx;
import com.jfinal.plugin.cron4j.Cron4jPlugin;
import com.jfinal.plugin.cron4j.ITask;

import java.util.Date;
import java.util.List;


/**
 * 定时任务service
 */
@Before(Tx.class)
public class TaskService {

    /**
     * 启动所有任务（启动项目时调用）
     */
    public void startAll() {
        System.out.println(ToolDateTime.format(new Date(), "yyyy-MM-dd HH:mm:ss.S") + "开始启动定时任务");
        List<SysTask> taskList = SysTask.dao.find("select * from sys_task");
        int count = 0;
        for (SysTask taskBase : taskList) {
            Cron4jPlugin cp = new Cron4jPlugin();
            cp.addTask(taskBase.getCron(), createTask(taskBase));  // cp中添加任务
            TaskConstant.getTaskMap().put(taskBase.getId(), cp);   // 任务id， cp
            if (taskBase.getStatus() == TaskConstant.TASK_STATU1) {
                // 状态等待运行
                cp.start();
                count++;
            }
        }
        System.out.println(ToolDateTime.format(new Date(), "yyyy-MM-dd HH:mm:ss.S") + "完成启动定时任务	已启动" + count + "个任务");
    }

    /**
     * 启动或停止任务
     *
     * @param taskId 任务id
     * @param status 状态 1:启动，2:停止
     */
    public void startOrStop(int taskId, int status) {
        SysTask sysTask = SysTask.dao.findById(taskId);
        sysTask.setStatus(status);
        sysTask.update();

        Cron4jPlugin cp = (Cron4jPlugin) TaskConstant.getTaskMap().get(taskId);
        if (status == TaskConstant.TASK_STATU1) {
            cp.start();
        } else if (status == TaskConstant.TASK_STATU2) {
            cp.stop();
        }
    }

    /**
     * 增加任务
     *
     * @param task
     */
    public void add(SysTask task) {
        task.setStatus(TaskConstant.TASK_STATU2); //默认停止
        task.save();

        Cron4jPlugin cp = new Cron4jPlugin();
        cp.addTask(task.getCron(), createTask(task));
        TaskConstant.getTaskMap().put(task.getId(), cp);
    }

    /**
     * 更新任务
     *
     * @param taskBase
     */
    public void update(SysTask taskBase) {
        Cron4jPlugin cp = (Cron4jPlugin) TaskConstant.getTaskMap().get(taskBase.getId());

        SysTask sysTaskOld = SysTask.dao.findById(taskBase.getId());
        try {
            // 原来的 状态为 不为 "停止“ ，停止原来的运行任务
            if (sysTaskOld.getStatus() == TaskConstant.TASK_STATU1 || sysTaskOld.getStatus() == TaskConstant.TASK_STATU3) {
                cp.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ErrorMsgException("任务停止失败，" + e.getMessage());
        }

        // 创建新的task
        cp = new Cron4jPlugin();
        cp.addTask(taskBase.getCron(), createTask(taskBase));
        try {
            if (taskBase.getStatus() == TaskConstant.TASK_STATU1) {
                cp.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ErrorMsgException("任务启动失败，" + e.getMessage());
        }

        taskBase.update();
        TaskConstant.getTaskMap().put(taskBase.getId(), cp);
    }

    /**
     * 删除任务
     *
     * @param id 任务id
     */
    public void delete(Integer id) {
        Cron4jPlugin cp = (Cron4jPlugin) TaskConstant.getTaskMap().get(id);
        SysTask task = SysTask.dao.findById(id);

        try {
            //停止任务
            if (task.getStatus() == TaskConstant.TASK_STATU1) {
                cp.stop();
            }
            cp = null;
            TaskConstant.getTaskMap().remove(id);
            task.delete();
        } catch (Exception e) {
            e.printStackTrace();
            throw new ErrorMsgException("任务停止失败，" + e.getMessage());
        } finally {
            cp = null;
            TaskConstant.getTaskMap().remove(id);
        }
    }

    /**
     * 立即执行
     */
    public void runAtSoon(SysTask taskBase) {
        createTask(taskBase).run();
        // 阻塞
    }

    /**
     * 创建一个任务
     *
     * @param taskBase 任务信息
     * @return ITask
     */
    private ITask createTask(SysTask taskBase) {
        ITask task = new TaskWraper(taskBase.getId(), taskBase.getTargetType(), taskBase.getTargetValue());
        return task;
    }

}