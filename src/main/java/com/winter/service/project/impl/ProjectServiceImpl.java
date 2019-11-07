package com.winter.service.project.impl;

import com.winter.mapper.ProjectMapper;
import com.winter.model.Project;
import com.winter.service.project.ProjectService;
import com.winter.utils.StringUtils;
import net.sf.mpxj.*;
import net.sf.mpxj.mpp.MPPReader;
import net.sf.mpxj.mspdi.MSPDIWriter;
import net.sf.mpxj.writer.ProjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created By Donghua.Chen on  2018/1/9
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    public static Logger logger = LoggerFactory.getLogger(ProjectServiceImpl.class);

    @Autowired
    private ProjectMapper projectMapper;


    @Override
    public Integer addProjectInfo(Project project) {
        return projectMapper.addProjectSelective(project);
    }

    @Transactional
    @Override
    public void readMmpFileToDB(File file) {
        try{
            //这个是读取文件的组件
            MPPReader mppRead = new MPPReader();
            //注意，如果在这一步出现了读取异常，肯定是版本不兼容，换个版本试试
            ProjectFile pf = mppRead.read(file);
            System.out.println(file.getName());
            //从文件中获取的任务对象
            List<Task> tasks = pf.getChildTasks();
            System.out.println("tasks.size() : " + tasks.size());
            //这个可以不用，这个list只是我用来装下所有的数据，如果不需要可以不使用
            List<Project> proList = new LinkedList<>();
            //这个是用来封装任务的对象，为了便于区别，初始化批次号，然后所有读取的数据都需要加上批次号
            Project pro = new Project();
            pro.setBatchNum(StringUtils.UUID());//生成批次号UUID
            //这个方法是一个递归方法
            getChildrenTask(tasks.get(0), pro ,proList, 0);
        }catch (MPXJException e) {
            logger.error(e.getMessage());
            throw new RuntimeException();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new RuntimeException();
        }
    }


    /**
     * 这个方法是一个递归
     * 方法的原理：进行读取父任务，如果下一层任务还是父任务，那么继续调用当前方法，如果到了最后一层，调用另外一个读取底层的方法
     * @param task
     * @param project
     * @param list
     * @param levelNum
     */
    @Override
    public void getChildrenTask(Task task, Project project, List<Project> list, int levelNum){
        if(task.getResourceAssignments().size() == 0){//这个判断是进行是否是最后一层任务的判断==0说明是父任务
            levelNum ++;//层级号需要增加，这个只是博主用来记录该层的层级数
            List<Task> tasks = task.getChildTasks();//继续获取子任务
            for (int i = 0; i < tasks.size(); i++) {//该循环是遍历所有的子任务
                if(tasks.get(i).getResourceAssignments().size() == 0){//说明还是在父任务层
                    System.out.println("+++++" + tasks.get(i));
                    Project pro = new Project();
                    if (project.getProjId() != null){//说明不是第一次读取了，因为如果是第一层，那么还没有进行数据库的添加，没有返回主键Id
                        pro.setParentId(project.getProjId());//将上一级目录的Id赋值给下一级的ParentId
                    }
                    pro.setBatchNum(project.getBatchNum());//批量号
                    pro.setImportTime(new Date());//导入时间
                    pro.setLevel(levelNum);//层级
                    pro.setTaskName(tasks.get(i).getName());//这个是获取文件中的“任务名称”列的数据
                    pro.setDurationDate(tasks.get(i).getDuration().toString());//获取的是文件中的“工期”
                    pro.setStartDate(tasks.get(i).getStart());//获取文件中的 “开始时间”
                    pro.setEndDate(tasks.get(i).getFinish());//获取文件中的 “完成时间”
                    pro.setResource(tasks.get(i).getResourceGroup());//获取文件中的 “资源名称”
                    this.addProjectInfo(pro);//将该条数据添加到数据库，并且会返回主键Id，用做子任务的ParentId,这个需要在mybatis的Mapper中设置
                    pro.setProjId(pro.getProjId());
                    //getResourceAssignment(tasks.get(i),pro,list,levelNum);
                    getChildrenTask(tasks.get(i), pro,list,levelNum);//继续进行递归，当前保存的只是父任务的信息
                }else{
                    getChildrenTask(tasks.get(i), project, list, levelNum);
                }
            }
        }else{//说明已经到了最底层的子任务了，那么就调用进行最底层数据读取的方法
            if (project.getProjId() != null){

                getResourceAssignment(task, project, list, levelNum);
            }
        }
    }

    public void getResourceAssignment(Task task, Project project, List<Project> proList, int levelNum){
        List<ResourceAssignment> list = task.getResourceAssignments();
        ResourceAssignment rs = list.get(0);
        System.out.println("task = [" + task.getName());
        Project pro = new Project();
        pro.setTaskName(task.getName());
        pro.setParentId(project.getProjId());
        pro.setLevel(levelNum);
        pro.setImportTime(new Date());
        pro.setBatchNum(project.getBatchNum());
        pro.setDurationDate(task.getDuration().toString());
        pro.setStartDate(rs.getStart());//注意，这个从ResourceAssignment中读取
        pro.setEndDate(rs.getFinish());//同上
        String resource = "";
        if(list.size() > 1){
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getResource() != null){
                    if(i < list.size() - 1){
                        resource += list.get(i).getResource().getName() + ",";
                    }else{
                        resource += list.get(i).getResource().getName();
                    }
                }
            }
        }else{

            if(list.size() > 0 && list.get(0).getResource() != null){
                resource = list.get(0).getResource().getName();
            }
        }
        if(!StringUtils.isEmpty(resource)){
            pro.setResource(resource);
        }
        this.addProjectInfo(pro);
        pro.setProjId(pro.getProjId());//将数据保存在数据库中,同样会返回主键
        proList.add(pro);

    }


    @Override
    public void writeMppFileToDB(String fileLocation, String batchNum, File file){
        try{
            MPPReader mppRead = new MPPReader();
            ProjectFile pf = mppRead.read(file);
            List<Project> projects =  projectMapper.getProjectsByBatchNum(batchNum);
            writeChildrenTaskToObj(projects,1,pf.addTask(), null);

            //生成文件
            ProjectWriter writer = new MSPDIWriter();

            try{
                writer.write(pf, "d:\\test.xml");
            }catch(IOException ioe){
                throw ioe;
            }
        }catch (MPXJException e) {
            logger.error(e.getMessage());
            throw new RuntimeException();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new RuntimeException();
        }
    }

    public void writeChildrenTaskToObj(List<Project> proList, int levelNum1, Task parentTask, Integer parentId){
        //首先从第一层开始读取
        List<Project> pList = getSubProjectList(proList,parentId,levelNum1);

        for (int i = 0; i < pList.size(); i++) {
            int levelNum = levelNum1;
            Project pro = pList.get(i);
            //然后利用parentId进行进一步的读取,并且这个可以进行判断是否是最底层
            List<Project> childrenList = getSubProjectList(proList,pro.getProjId(),levelNum + 1);
            //这个判断很重要，如果size为0，说明当前的层级是最底层
            if(childrenList.size() > 0){
                //说明是父任务，进行父任务的写入，然后进行下一次递归
                Task task = parentTask.addTask();
                task.setName(pro.getTaskName());
                task.setDuration(Duration.getInstance(5, TimeUnit.DAYS));
                task.setStart(pro.getStartDate());
                task.setFinish(pro.getEndDate());
                if (levelNum ==  1){//如果是读取第一层
                    task.setOutlineLevel(1);
                    task.setUniqueID(1);
                    task.setID(1);
                }else{
                    task.setOutlineLevel(parentTask.getOutlineLevel() + 1);
                    task.setUniqueID(parentTask.getUniqueID() + 1);
                    task.setID(parentTask.getID() + 1);
                }
                levelNum ++;
                //进行递归写入
                writeChildrenTaskToObj(proList, levelNum,task, pro.getProjId());
            }else{//说明当前层级为最底层
                writeResourceAssignmentToObj(pro, pro.getParentId(), parentTask);
            }

        }

    }

    public void writeResourceAssignmentToObj(Project pro, int parentId, Task parentTask){

        Task task = parentTask.addTask();
        task.setName(pro.getTaskName());
        task.setDuration(Duration.getInstance(5, TimeUnit.DAYS));
        task.setStart(pro.getStartDate());
        task.setFinish(pro.getEndDate());
        task.setResourceGroup(pro.getResource());
        task.setOutlineLevel(parentTask.getOutlineLevel() + 1);
        task.setUniqueID(parentTask.getUniqueID() + 1);
        task.setID(parentTask.getID() + 1);
    }

    /**
     * 获取子任务列表
     * @Author: Donghua.Chen
     * @Description:
     * @Date: 2018/1/12
     * @param proList
     * @param parentId
     */
    private List<Project> getSubProjectList(List<Project> proList, Integer parentId, Integer levelNum){

        List<Project> subList = new LinkedList<>();
        List<Project> rsList = new LinkedList<>();
        if(levelNum != null){
            for (int i = 0; i < proList.size(); i++) {
                Project pro = proList.get(i);

                    if(pro.getLevel().equals(levelNum)){
                        subList.add(pro);
                    }
            }

        }else{
            subList = proList;
        }

        if(parentId != null){
            for (int i = 0; i < subList.size(); i++) {
                Project pro = subList.get(i);
                if(pro.getParentId().equals(parentId)){
                    rsList.add(pro);
                }
            }

        }
        if (parentId != null){
            return rsList;
        }else {
            return subList;
        }

    }

}
