package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Blog queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return null;
        }
        // 查询blog有关的用户
        this.queryBlogUser(blog);
        // 查询blog是否被点赞
        this.isBlogLiked(blog);
        return blog;
    }

    @Override
    public List<Blog> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return records;
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public List<Blog> queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = this.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return records;
    }

    @Override
    public boolean likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            // 点过赞
            boolean updated = this.update().setSql("liked = liked - 1").eq("id", id).update();
//            stringRedisTemplate.opsForValue().decrement(RedisConstants.BLOG_LIKED_NUM + id); // TODO: 通过定时任务, 定时将点赞数持久化到数据库中
            if (updated)
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        } else {
            // 未点赞
            boolean updated = this.update().setSql("liked = liked + 1").eq("id", id).update();
//            stringRedisTemplate.opsForValue().increment(RedisConstants.BLOG_LIKED_NUM + id);
            if (updated)
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public Long saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        this.save(blog);
//        stringRedisTemplate.opsForValue().set(RedisConstants.BLOG_LIKED_NUM + blog.getId(), String.valueOf(0));
        // 返回id
        return blog.getId();
    }

    @Override
    public List<UserDTO> queryBlogLikes(Long id) {
        // 查询Top5点赞用户, 按照时间排序(score是时间戳)
        Set<String> top5 = stringRedisTemplate.opsForZSet()
                .range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Collections.emptyList();
        }
        // 解析出其中的用户id
        List<Long> userIds = top5.stream().map(Long::parseLong).collect(Collectors.toList());
        // 根据用户id查询用户 转为UserDTO返回
        Map<Long, UserDTO> userDTOMap = userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> BeanUtil.copyProperties(user, UserDTO.class)));

        List<UserDTO> userDTOList = userIds.stream()
                .map(userDTOMap::get)
                .collect(Collectors.toList());

        return userDTOList;
    }
}
