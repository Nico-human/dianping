package com.hmdp.service;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Blog queryBlogById(Long id);

    List<Blog> queryHotBlog(Integer current);

    List<Blog> queryMyBlog(Integer current);

    boolean likeBlog(Long id);

    Long saveBlog(Blog blog);

    List<UserDTO> queryBlogLikes(Long id);
}
