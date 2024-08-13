package com.hmdp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result<Long> saveBlog(@RequestBody Blog blog) {
        Long blogId = blogService.saveBlog(blog);
        return Result.ok(blogId);
    }

    @PutMapping("/like/{id}")
    public Result<Boolean> likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        boolean liked = blogService.likeBlog(id);
        return Result.ok(liked);
    }

    @GetMapping("/of/me")
    public Result<List<Blog>> queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<Blog> records = blogService.queryMyBlog(current);
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result<List<Blog>> queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<Blog> records = blogService.queryHotBlog(current);
        return Result.ok(records);
    }

    @GetMapping("/{id}")
    public Result<Blog> queryBlogById(@PathVariable Long id) {
        Blog blog = blogService.queryBlogById(id);
        return blog != null ? Result.ok(blog) : Result.fail("笔记不存在");
    }

    @GetMapping("/likes/{id}")
    public Result<List<UserDTO>> queryBlogLikes(@PathVariable Long id) {
        return Result.ok(blogService.queryBlogLikes(id));
    }

    // BlogController  根据id查询博主的探店笔记
    @GetMapping("/of/user")
    public Result<List<Blog>> queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

}