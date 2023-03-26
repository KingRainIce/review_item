Redis笔记:实战篇
===========

1\. 短信登录
----------------------------------------

项目整体架构如下：

[![](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302251101592.png)

](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221025174859439.png "image-20221025174859439")

通过Nginx将前端请求转发到后端服务器中，Redis与MySQL作为数据库。

### 1.1 导入项目

1. 创建hmdp数据库，导入SQL文件 `hmdp.sql`

   [![](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302251101134.png)
   
   ](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221025171044276.png)

   表介绍：

   *   tb_user：用户表
   *   tb\_user\_info：用户详情表
   *   tb_shop：商户信息表
   *   tb\_shop\_type：商户类型表
   *   tb_blog：用户日记表（达人探店日记）
   *   tb_follow：用户关注表
   *   tb_voucher：优惠券表
   *   tb\_voucher\_order：优惠券订单表

2. 导入后端项目：`hm-dianping`

   将application.yaml文件中MySQL与Redis配置修改为自己的

   之后启动SpringBoot项目，并访问 `http://localhost:8081/shop-type/list`，显示出数据则说明配置成功！

   [![](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221025172132464.png)
   
   ](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221025172132464.png)

3. 导入前端项目：配置nginx

   由于我使用的是Mac M1，用homebrew安装的nginx，分享一下我的配置方法

   1. 采用如下命令启动nginx

      ```ebnf
      start nginx.exe
      ```

   2. 访问 `localhost:8080`，即可成功

### 1.2 基于Session的短信登录

![image-20230225110652729](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302251106831.png)

#### 1.2.1 流程分析

##### 服务端发送短信验证码流程

1.  服务端接收到手机号，校验手机号是否符合规则，符合则进入下一步
2.  生成验证码，并将验证码保存到Session中
3.  发送验证码

##### 短信验证码登录与注册流程

1.  用户提交手机号与验证码，服务端校验验证码，若正确，则进入下一步
2.  根据手机号查询信息
    *   若用户存在，登陆成功，保存用户到Session
    *   若用户不存在，用户为新用户，则将其保存到数据库中，保存用户到Session

##### 校验登录状态

1.  用户访问网站，携带Cookie，通过Cookie中的SessionID获取对应的Session，从Session中获取用户信息，判断信息是否有效
    *   若信息有效，用户存在，则将信息保存到ThreadLocal中，便于后续使用
    *   若信息无效，用户不存在，结束

> ThreadLocal 可以保证多线程修改用户下的线程安全问题

#### 1.2.2 功能实现

##### 发送短信验证码

1. 更改controller包下UserController中的sendCode方法

   ```java
   public Result sendCode(String phone, HttpSession session) {
           // 1.校验手机号
           if (RegexUtils.isPhoneInvalid(phone)) {
               // 2.如果不符合，返回错误信息
               return Result.fail("手机号格式错误！");
           }
           // 3.符合，生成验证码
           String code = RandomUtil.randomNumbers(6);
           // 4.保存验证码到Session
           session.setAttribute("code", code);
           // 5.发送验证码
           log.debug("发送验证码：{}", code);
           return Result.ok();
       }
   ```

2. 在UserServiceImpl中实现该方法

   注意：验证码的发送用log输出日志模拟一下即可，表示发送成功

   ```java
   @PostMapping("/login")
   public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
       // TODO 实现登录功能
       return userService.login(loginForm, session);
   }
   ```

##### 登录与注册

1. 更改Controller包下UserController中的login方法

   ```java
   @PostMapping("/login")
   public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
       // TODO 实现登录功能
       return userService.login(loginForm, session);
   }
   ```

2. 在UserService中实现该方法

   ```java
   @Override
   public Result login(LoginFormDTO loginForm, HttpSession session) {
       // 1.校验手机号
       String phone = loginForm.getPhone();
       if (RegexUtils.isPhoneInvalid(phone)) {
           return Result.fail("手机号格式错误！");
       }
       // 2.校验验证码
       String cacheCode = (String) session.getAttribute("code");
       String code = loginForm.getCode();
       if (cacheCode == null || !cacheCode.equals(code)) {
           // 3.不一致报错
           return Result.fail("验证码错误");
       }
       // 4.一致，根据手机号查询用户
       User user = query().eq("phone", phone).one();
       // 5.判断用户是否存在
       if (user == null) {
           // 6.不存在，创建用户并保存
           user = createUserWithPhone(phone);
       }
       // 7.保存用户信息到Session
       session.setAttribute("user", user);
       return Result.ok();
   }
   
   private User createUserWithPhone(String phone) {
       User user = new User();
       user.setPhone(phone);
       user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
       save(user);
       return user;
   }
   ```

##### 登录校验拦截器

![image-20230225125738494](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302251257643.png)

情景分析：

登录完成之后，一些请求需要校验用户的登录状态，然后才能允许执行进一步的操作（比如查看订单等）

如果在每个请求的方法中都添加校验逻辑，会增加很多冗余代码。

因此，我们采用登录校验拦截器，在请求到达每个Controller之前，对其做校验，获取用户信息。

为了避免线程安全问题，将用户信息保存到ThreadLocal中，这样每个请求对应着自己的用户信息，互不干扰。

1. 在Interceptors包下创建LoginInterceptor

   UserHolder其实是一个工具类，用于将用户信息保存到ThreadLocal以及从ThreadLocal中取用户信息

   移除用户是为了防止内存泄露

   ```java
   public class LoginInterceptor implements HandlerInterceptor {
   
       @Override
       public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
           // 1.获取session
           HttpSession session = request.getSession();
           // 2.获取session中的用户
           Object user = session.getAttribute("user");
           // 3.判断用户是否存在
           if (user == null) {
               // 4.不存在则拦截
               response.setStatus(401);
               return false;
           }
           // 5.存在则保存用户信息到ThreadLocal
           UserHolder.saveUser((User) user);
           // 6.放行
           return true;
       }
   
       @Override
       public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
           // 移除用户
           UserHolder.removeUser();
       }
   }
   ```

   > 之所以要使用UserHolder，是因为当前请求所创建的这个线程有多处要用到user，当请求完后需要删除防止内存泄漏

2. 在config下创建MVCConfig类，将拦截器进行配置，对于一些不必要拦截的路径进行排除

   ```java
   @Configuration
   public class MVCConfig implements WebMvcConfigurer {
   
       @Override
       public void addInterceptors(InterceptorRegistry registry) {
           registry.addInterceptor(new LoginInterceptor())
                   .excludePathPatterns("/user/code", "/user/login", "/blog/hot",
                           "/shop/**", "/shop-type/**", "/voucher/**", "/upload/**");
       }
   }
   ```

3. 更改UserController中的me方法

   ```java
   @GetMapping("/me")
   public Result me(){
       User user = UserHolder.getUser();
       return Result.ok(user);
   }
   ```




##### 隐藏敏感信息![image-20230225140246489](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302251402599.png)

访问到个人主页时，后端返回的信息过多。为了隐藏用户敏感信息，将用户信息存入Session时，需要将User转为UserDTO对象。修改流程如下：

1. 将UserServiceImpl中的login方法存入Session的代码更改为：

   ```java
   // 7.保存用户信息到Session
   session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
   ```

   > 此方法是用来转换对象的属性的

2. 更改LoginInterceptor中保存用户信息到ThreadLocal的代码：

   ```java
   // 5.存在则保存用户信息到ThreadLocal
   UserHolder.saveUser((UserDTO) user);
   ```

3. 最后将UserHolder工具类中的User全部更改为UserDTO


更改之后重启SpringBoot，进行登录测试，此时对应的me请求返回结果就没有敏感信息了

[![](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302251412153.png)

](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221025210030885.png "image-20221025210030885")

#### 1.2.3 集群Session共享问题

session共享问题：多台Tomcat并不共享session存储空间，当请求切换到不同tomcat服务时导致数据丢失的问题。

即使采用Tomcat之间拷贝Session机制，也存在拷贝时间的延迟以及内存占用问题

Session的替代方案必须满足：数据共享、内存存储、key-value结构，redis完美解决！！！

### 1.3 基于Redis的短信登录

#### 1.3.1 流程分析![image-20230225165136623](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302251651748.png)

##### 服务端发送短信验证码流程

与Session的流程基本一致

1.  服务端接收到手机号，校验手机号是否符合规则，符合则进入下一步
2.  生成验证码，并将验证码保存到Redis中
    *   采用手机号作为key：`phone:xxxxx`，验证码作为value，值类型为string
    *   设置一定时间的有效期
3.  发送验证码

##### 短信验证码登录与注册流程

1.  用户提交手机号与验证码，服务端校验验证码，若正确，则进入下一步
2.  根据手机号查询信息
    *   若用户存在，登陆成功，保存用户到Redis
    *   若用户不存在，用户为新用户，则将其保存到数据库中，保存用户到Redis
    *   Redis中的用户信息：采用Token作为key，用户信息作为value，采用Hash结构存储
        *   Token是放于请求头中的，为了确保用户隐私与值唯一性，该Token值需要以一定规则生成
        *   设置一定时间的有效期
3.  将Token返回给前端

##### 校验登录状态

1.  用户访问网站，发起请求中携带着Token，通过Token从Redis中获取用户信息，判断信息是否有效
    *   若信息有效，用户存在，则将信息保存到ThreadLocal中，便于后续使用，并更新Token的有效期
    *   若信息无效，用户不存在

#### 1.3.2 功能实现

##### 发送短信验证码

```java
@Override
public Result sendCode(String phone, HttpSession session) {
    // 1.校验手机号
    if (RegexUtils.isPhoneInvalid(phone)) {
        // 2.如果不符合，返回错误信息
        return Result.fail("手机号格式错误！");
    }
    // 3.符合，生成验证码
    String code = RandomUtil.randomNumbers(6);
    // 4.保存验证码到Redis
    template.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
    // 5.发送验证码
    log.debug("发送短信验证码成功，验证码：{}", code);
    return Result.ok();
}
```

##### 登录与注册

```java
@Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.校验验证码
        String cacheCode = template.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 3.不一致报错
            return Result.fail("验证码错误");
        }
        // 4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建用户并保存
            user = createUserWithPhone(phone);
        }
        // 7.保存用户信息到Redis
        // 7.1.生成Token
        String token = UUID.randomUUID().toString();
        // 7.2.将User转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((name, value) -> value.toString()));
        // 7.3.存储
        template.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 7.4.设置有效期
        template.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8.返回Token
        return Result.ok(token);
    }
```

![](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302252259491.png)

上述报错发生的原因是UserDTO中的id字段为Long类型，而Redis存储时无法使用Long类型数据

为了防止发生上述报错，可以看到7.2步骤中将User转为Hash存储时，通过BeanUtil方法，将所有字段的均转为了String类型

##### 登录校验拦截器

只需更改preHandle中的内容

```java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    // 1.获取请求头中的Token
    String token = request.getHeader("authorization");
    if (StrUtil.isBlank(token)) {
        response.setStatus(401);
        return false;
    }
    // 2.获取Redis中的用户
    Map<Object, Object> userMap = template.opsForHash().entries(LOGIN_USER_KEY + token);

    // 3.判断用户是否存在
    if (userMap.isEmpty()) {
        // 4.不存在则拦截
        response.setStatus(401);
        return false;
    }
    // 5.将查询到的Hash数据转为UserDTO对象
    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    // 6.存在则保存用户信息到ThreadLocal
    UserHolder.saveUser((UserDTO) userDTO);
    // 7.刷新Token有效期
    template.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
    // 8.放行
    return true;
}
```

#### 1.3.3 拦截器优化![image-20230225230919350](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302252309479.png)

情景分析：

通过登录校验拦截器进行刷新Token的有效时间可能会存在这样一个问题：

*   用户的请求并没有通过登录校验拦截器（如访问主页等无需校验的操作），但是用户仍然一致活跃在网页中。如果超过指定时间Token过期后，用户需要重新进行登录，这样会造成不好的用户体验。

解决方案：

将之前的登录校验拦截器拆分为两个拦截器，

第一个拦截器用于：获取Token，通过Redis查询用户，保存到ThreadLocal，刷新Token有效期

第二拦截器用于：查询ThreadLocal，判断是否存在用户，存在则放行，不存在则拦截

1. 复制之前的 LoginInterceptor，命名为 RefreshTokenInterceptor，对preHandle方法做修改

   ```java
   @Override
   public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       // 1.获取请求头中的Token
       String token = request.getHeader("authorization");
       if (StrUtil.isBlank(token)) {
           return true;
       }
       // 2.获取Redis中的用户
       Map<Object, Object> userMap = template.opsForHash().entries(LOGIN_USER_KEY + token);
       // 3.判断用户是否存在
       if (userMap.isEmpty()) {
           return true;
       }
       // 4.将查询到的Hash数据转为UserDTO对象
       UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
       // 5.存在则保存用户信息到ThreadLocal
       UserHolder.saveUser((UserDTO) userDTO);
       // 6.刷新Token有效期
       template.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
       // 7.放行
       return true;
   }
   ```

2. 修改LoginInterceptor中的preHandle方法

   ```java
   @Override
   public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       // 判断是否需要拦截
       if (UserHolder.getUser() == null) {
           response.setStatus(401);
           return false;
       }
       return true;
   }
   ```

3. 修改MVCConfig类，注意两个拦截器要设置先后顺序

   ```java
   @Override
   public void addInterceptors(InterceptorRegistry registry) {
       registry.addInterceptor(new LoginInterceptor())
           .excludePathPatterns("/user/code", "/user/login", "/blog/hot",
                                "/shop/**", "/shop-type/**", "/voucher/**", "/upload/**")
           .order(1);
       registry.addInterceptor(new RefreshTokenInterceptor(template)).order(0);
   
   }
   ```

2\. 商户查询缓存
------------------------------------------------

### 2.1 缓存

![image-20230225235143157](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302252351233.png)

缓存为数据交换的缓冲区（cache），是数据存储的临时地方，读写性能较高

#### 缓存作用：

1.  降低后端负载
2.  提高读写效率，降低响应时间

#### 缓存成本：

1.  数据一致性成本：MySQL与Redis数据一致
2.  代码维护成本
3.  运维成本

### 2.2 添加商户缓存

添加缓存之前：客户端直接请求数据库，数据库查询得到数据后返回给客户端

添加缓存之后：客户端先请求Redis，Redis若有对应数据，则直接返回；若没有，再去查询数据库，并将数据写入到Redis

#### 2.2.1 流程分析

![image-20230226002742353](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302260027477.png)

根据ID查询商户缓存流程：

根据商铺ID从Redis中查询缓存，判断缓存是否命中

*   若命中，则返回商铺信息
*   若未命中，则根据ID从MySQL中查询
    *   若MySQL中存在，则将商铺信息写入Redis，最后返回商铺信息
    *   若MySQL中不存在，则返回error

#### 2.2.2 功能实现

1. 更改ShopController中的queryShopById方法

   ```java
   @GetMapping("/{id}")
   public Result queryShopById(@PathVariable("id") Long id) {
       return shopService.queryById(id);
   }
   ```

2. 依据之前分析的流程，在ShopService中实现该方法

   ```java
   @Override
   public Result queryById(Long id) {
       // 1.从Redis中查询商铺缓存
       String shopJson = template.opsForValue().get(CACHE_SHOP_KEY + id);
       // 2.判断是否存在
       if (StrUtil.isNotBlank(shopJson)) {
           // 3.存在则直接返回
           Shop shop = JSONUtil.toBean(shopJson, Shop.class);
           return Result.ok(shop);
       }
       // 4.不存在则根据ID查询数据库
       Shop shop = getById(id);
       // 5.不存在返回错误
       if (shop == null) {
           return Result.fail("店铺不存在！");
       }
       // 6.存在则写入Redis
       template.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
       return Result.ok(shop);
   }
   ```

### 2.3 缓存更新策略

为了解决缓存与数据库中实际信息不一致的问题，需要引入缓存更新策略。

#### 2.3.1 策略类型

![image-20230226105837554](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302261058653.png)

业务场景选择：

*   低一致性需求：使用内存淘汰机制。例如：店铺类型等长时间内不会改变的缓存数据
*   高一致性需求：主动更新，并以超时剔除作为兜底方案。例如：店铺详情查询的缓存

#### 2.3.2 主动更新策略

##### 方式

1.  Cache Aside Pattern：缓存调用者在更新数据库同时更新缓存
2.  Read/Write Through Pattern：缓存与数据库整合为一个服务，由服务维护一致性。调用者只需调用服务，无需关心一致性问题。
3.  Write Behind Caching Pattern：调用者只操作缓存，由其他线程异步地将缓存数据持久化到数据库中，最终保持一致

第二种策略虽然简化了调用者的操作，但是维护这样一个服务复杂度较高。

第三种策略存在有一致性与可靠性问题。若缓存服务器宕机，则对于缓存所做的操作（内存层面）都会丢失。

第一种策略虽然需要手写业务逻辑，但是可控性更高，适用范围广。

##### 考虑

1. 删除缓存 or 更新缓存？

   *   更新缓存：每次更新数据库时都对缓存进行更新，会导致较多的无效写操作。因此可能在此期间并没有人进行读操作。
   *   删除缓存：更新数据库时让缓存失效，等到下一次有人查询时再通过数据库添加缓存。

2. 如何保证缓存与数据库的操作同时成功或失败？原子性问题

   *   单体系统：将缓存与数据库操作放在一个事务中
   *   分布式系统：利用TCC等分布式事务方案

3. 先操作缓存还是先操作数据库？线程安全问题

   * 先删缓存，再操作数据库：![image-20230227103540773](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302271035871.png)

     一个线程删完缓存之后，还未来得及更新数据，另一个线程便进行查询操作，而查询缓存未命中，则查询数据库，并又将旧的数据写入缓存，此时第一个线程才更新完数据。

     线程不安全，造成缓存与数据库不一致的情况

   * 先操作数据库，再删缓存：![image-20230227104013268](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302271040352.png)

     一个线程进行查询操作，但是查询缓存未命中，则查询数据库并得到数据。而此时另一个线程进行更新数据库操作，该操作对于第一个线程是不可见的，因此第一个线程在写入缓存时，仍然写入的是旧数据。

   * 方案二发生的可能性更低，因为需要满足缓存失效、数据库更新快于写入缓存等极端条件。因此选择方案二。

#### 2.3.3 代码实现

1. 在查询代码的写入缓存逻辑中，添加缓存超时时间，作为保底方案。

   ```java
   // 6.存在则写入Redis
   template.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
   ```

2. 更改ShopController中的updateShop方法

   ```java
   @PutMapping
   public Result updateShop(@RequestBody Shop shop) {
       // 写入数据库
       return shopService.update(shop);
   }
   ```

3. 在ShopService中实现该方法：注意为确保缓存与数据库操作的原子性，需要添加事务注解

   ```java
   @Override
   @Transactional
   public Result update(Shop shop) {
       Long id = shop.getId();
       if (id == null) {
           return Result.fail("店铺id不能为空");
       }
       // 1.更新数据库
       updateById(shop);
       // 2.删缓存
       template.delete(CACHE_SHOP_KEY + shop.getId());
       // 3.返回
       return Result.ok();
   }
   ```

### 2.4 缓存穿透

#### 2.4.1 介绍与解决思路

客户端请求的数据在缓存和数据库中都不存在，最终这些请求均会到达数据库。若多线程高并发请求，则会使数据库崩溃。

解决方案：![image-20230227123329111](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302271233221.png)

1.  缓存空对象：当请求到达数据库，数据库也不存在时，则缓存一个空对象，之后再次请求时，缓存命中并返回空对象。
    *   优点：实现简单，维护方便
    *   缺点：额外内存消耗（可设置TTL解决）、短期不一致（可能缓存空对象后，又插入了真实数据，造成缓存与数据库不一致）
2.  布隆过滤器
    *   优点：内存占用少，没有多余key
    *   缺点：实现复杂，存在误判可能性

#### 2.4,2 代码实现

![image-20230227123457142](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302271234263.png)

修改ShopServiceImpl中的queryById方法

需要注意的是：isNotBlank方法只有在为Null以及为””的情况下返回false

*   如果其返回true，则表示缓存中存在店铺信息，直接返回信息
*   如果其返回false，则需进一步判断是Null还是””
    *   如果是””，则代表已设置了空对象，报错
    *   如果是Null，则代表当前缓存中不存在该信息，则需要进一步查询数据库

```java
@Override
public Result queryById(Long id) {
    // 1.从Redis中查询商铺缓存
    String shopJson = template.opsForValue().get(CACHE_SHOP_KEY + id);
    // 2.判断是否存在
    if (StrUtil.isNotBlank(shopJson)) {
        // 3.存在则直接返回
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        return Result.ok(shop);
    }
    // 判断命中的是否是空值
    if (shopJson != null) {
        return Result.fail("店铺信息不存在！");
    }
    // 4.不存在则根据ID查询数据库
    Shop shop = getById(id);
    // 5.不存在，将空值写入Redis，返回错误
    if (shop == null) {
        template.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        return Result.fail("店铺不存在！");
    }
    // 6.存在则写入Redis
    template.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    return Result.ok(shop);
}
```

### 2.5 缓存雪崩

在同一时段有大量的缓存key同时失效或Redis宕机，导致大量请求进入数据库，带来巨大压力

解决方案：

1.  给不同key设置随机的TTL值
2.  利用Redis集群提高服务的可用性
3.  给缓存业务添加降级限流策略
4.  给业务添加多级缓存

### 2.6 缓存击穿

缓存击穿也被称为热点key问题，就是一个被**高并发访问**并且**缓存重建业务较复杂**的key突然失效了，无数请求进入数据库，在瞬间给数据库造成巨大冲击。

##### 解决方案：

![image-20230227203341108](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302272033234.png)

1. 互斥锁：并发线程中只有一个线程获取到锁，进行缓存重建操作，重建完成并释放锁之后，其他线程再次查询缓存。

2. 逻辑过期：为缓存设置逻辑过期时间，若某个线程发现逻辑时间已过期，便去获取互斥锁，获取成功之后去开启新线程重建缓存，其直接返回过期的数据即可。

   其他线程访问时也是同理，若其发现逻辑时间过期，则去获取互斥锁，若获取失败，说明有线程正在重建缓存，其直接返回过期数据

##### 对比：

1. 互斥锁没有额外内存消耗，实现简单，可以保证一致性

   但互斥锁的性能较差，且存在死锁风险

2. 逻辑过期线程无需等待，性能较好

   但其不保证一致性，有额外的内存消耗，实现较为复杂

#### 2.6.1 代码实现

##### 基于互斥锁

![image-20230227204546630](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302272045725.png)

```java
@Override
public Result queryById(Long id) {
    Shop shop = queryWithMutex(id);
    if (shop == null) {
        return Result.fail("店铺不存在！");
    }
    return Result.ok(shop);
}

public Shop queryWithMutex(Long id) {
    // 1.从Redis中查询商铺缓存
    String shopJson = template.opsForValue().get(CACHE_SHOP_KEY + id);
    // 2.判断是否存在
    if (StrUtil.isNotBlank(shopJson)) {
        // 3.存在则直接返回
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        return shop;
    }
    // 判断命中的是否是空值
    if (shopJson != null) {
        return null;
    }
    // 4.实现缓存重建
    // 4.1 获取互斥锁
    String lockKey = LOCK_SHOP_KEY + id;
    Shop shop = null;
    try {
        boolean isLock = tryLock(lockKey);
        // 4.2 判断是否获取成功
        if (!isLock) {
            // 4.3 失败，休眠并充实
            Thread.sleep(50);
            return queryWithMutex(id);
        }
        // 4.4 成功，则根据id查询数据库
        shop = getById(id);
        // 5.不存在，将空值写入Redis，返回错误
        if (shop == null) {
            template.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在则写入Redis
        template.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    } finally {
        // 7.释放互斥锁
        unlock(lockKey);
    }
    return shop;
}
```

##### 基于逻辑过期

![image-20230227223229818](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302272232915.png)

```java
public Shop queryWithLogicalExpire(Long id) {
    // 1.从Redis中查询商铺缓存
    String shopJson = template.opsForValue().get(CACHE_SHOP_KEY + id);
    // 2.判断是否存在
    if (StrUtil.isBlank(shopJson)) {
        // 3.不存在直接返回
        return null;
    }
    // 4.命中，将json反序列化为对象
    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
    LocalDateTime expireTime = redisData.getExpireTime();
    // 5.判断是否过期
    // 5.1 未过期，直接返回店铺信息
    if (expireTime.isAfter(LocalDateTime.now())) {
        return shop;
    }
    // 5.2 已过期，重建缓存
    // 6.缓存重建
    // 6.1 获取互斥锁
    String lockKey = LOCK_SHOP_KEY + id;
    boolean isLock = tryLock(lockKey);
    // 6.2 判断获取锁是否成功
    if (isLock) {
        // 6.3 成功，开启线程池，实现缓存重建
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                // 重建缓存
                this.saveShop2Redis(id, 20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁
                unlock(lockKey);
            }
        });
    }
    // 6.4 返回过期的商铺信息
    return shop;
}
```

### 2.7 缓存工具封装

为使得解决缓存问题变得更加通用，封装一个缓存工具类，采用了泛型方法、函数式编程、Lambda表达式实现

*   set：存储缓存键值对
*   setWithLogicalExpire：存储带有逻辑过期时间的缓存键值对
*   queryWithPassThrough：用于解决缓存穿透的查询
*   queryWithLogicalExpire：用于解决缓存击穿的查询

具体流程为：

1. 在utils包下创建CacheClient类

2. 添加如下四个方法

   ```java
   @Slf4j
   @Component
   public class CacheClient {
   
       @Autowired
       private StringRedisTemplate template;
   
       public void set(String key, Object value, Long time, TimeUnit unit) {
           template.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
       }
   
       public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
           // 设置逻辑过期
           RedisData redisData = new RedisData();
           redisData.setData(value);
           redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
           template.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, unit);
       }
   
       public <R, ID> R queryWithPassThrough(String keyPrefix,
                                             ID id, Class<R> type,
                                             Function<ID, R> dbFallBack,
                                             Long time, TimeUnit unit) {
           String key = keyPrefix + id;
           // 1.从Redis中查询商铺缓存
           String json = template.opsForValue().get(key);
           // 2.判断是否存在
           if (StrUtil.isNotBlank(json)) {
               // 3.存在则直接返回
               return JSONUtil.toBean(json, type);
           }
           // 判断命中的是否是空值
           if (json != null) {
               return null;
           }
           // 4.不存在则根据ID查询数据库
           R r = dbFallBack.apply(id);
           // 5.不存在，将空值写入Redis，返回错误
           if (r == null) {
               template.opsForValue().set(key, "", time, unit);
               return null;
           }
           // 6.存在则写入Redis
           this.set(key, JSONUtil.toJsonStr(r), time, unit);
           return r;
       }
   
       private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
   
       public <R, ID> R queryWithLogicalExpire(String keyPrefix,
                                               ID id, Class<R> type,
                                               Function<ID, R> dbFallBack,
                                               Long time, TimeUnit unit) {
           String key = keyPrefix + id;
           // 1.从Redis中查询商铺缓存
           String json = template.opsForValue().get(key);
           // 2.判断是否存在
           if (StrUtil.isBlank(json)) {
               // 3.不存在直接返回
               return null;
           }
           // 4.命中，将json反序列化为对象
           RedisData redisData = JSONUtil.toBean(json, RedisData.class);
           R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
           LocalDateTime expireTime = redisData.getExpireTime();
           // 5.判断是否过期
           // 5.1 未过期，直接返回店铺信息
           if (expireTime.isAfter(LocalDateTime.now())) {
               return r;
           }
           // 5.2 已过期，重建缓存
           // 6.缓存重建
           // 6.1 获取互斥锁
           String lockKey = LOCK_SHOP_KEY + id;
           boolean isLock = tryLock(lockKey);
           // 6.2 判断获取锁是否成功
           if (isLock) {
               // 6.3 成功，开启线程池，实现缓存重建
               CACHE_REBUILD_EXECUTOR.submit(() -> {
                   try {
                       R r1 = dbFallBack.apply(id);
                       this.setWithLogicalExpire(key, r1, time, unit);
                   } catch (Exception e) {
                       throw new RuntimeException(e);
                   } finally {
                       // 释放锁
                       unlock(lockKey);
                   }
               });
           }
           // 6.4 返回过期的商铺信息
           return r;
       }
   
       private boolean tryLock(String key) {
           Boolean flag = template.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
           return BooleanUtil.isTrue(flag);
       }
   
       private void unlock(String key) {
           template.delete(key);
       }
   }
   ```

3\. 优惠券秒杀
--------------------------------------------

### 3.1 全局唯一ID

#### 3.1.1 介绍

当用户进行优惠券秒杀时，会生成优惠券订单。如果订单编号采用数据库自增ID便会存在如下问题：

1.  ID规律明显
2.  会受到当前表数据量的限制

因此需要全局唯一ID生成器，用于在分布式系统下生成全局唯一ID其满足：唯一性、高可用、高性能、递增性、安全性该ID的设计规则如下：

*   其二进制由64个bit组成：![image-20230228123405679](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302281234776.png)
    *   最高位第63位为符号位，始终为0
    *   62~32位为时间戳，共31个bit
    *   31~0位为序列号，共32个bit：序列号的自增是通过Redis的increment自增实现

#### 3.1.1 代码实现

代码实现流程如下：

在utils包下创建RedisIdWorker类。

其中需要注意最终结果的返回需要将时间戳与序列号进行拼接，采用移位 \+ 或运算

```java
@Component
public class RedisIdWorker {

    // 2021-01-01 00:00:00
    private static final long BEGIN_STAMP = 1640995200L;

    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate template;

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_STAMP;
        // 2.生成序列号
        // 2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = template.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3.拼接并返回,+可以取代|
        return timestamp << COUNT_BITS | count;
    }
}
```

### 3.2 优惠券秒杀下单

#### 3.2.1 流程分析

数据库中有两张表：

*   tb_voucher：优惠券的基本信息，优惠金额、使用规则等
*   tb\_seckill\_voucher：优惠券的库存、开始抢购时间，结束抢购时间。特价优惠券才需要填写这些信息

voucher中存储了优惠券的基本信息，而seckill_voucher是特价优惠券，对优惠券添加了额外的抢购信息。

我们需要向借助于Postman向服务发起请求，添加特价优惠券。

注意当前时间必须在beginTime与endTime的时间段内，否则前端页面中不会显示出已添加的特价优惠券。![](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221031122855313.png)


秒杀下单流程分析：![image-20230228214118731](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302282141839.png)

1.  提交优惠券ID
2.  查询优惠券信息，判断秒杀是否开始与结束、库存是否充足
3.  扣减库存，创建订单，返回订单ID

#### 3.2.2 代码实现

1. 修改VoucherOrderController中的seckillVoucher方法

   ```java
   @PostMapping("seckill/{id}")
   public Result seckillVoucher(@PathVariable("id") Long voucherId) {
       return voucherOrderService.seckillVoucher(voucherId);
   }
   ```

2. 实现该方法

3. ```java  
   @Override  
   @Transactional  
   public Result seckillVoucher(Long voucherId) {  
   // 1.查询优惠券  
   SeckillVoucher voucher = seckillVoucherService.getById(voucherId);  
   // 2.判断秒杀是否开始与结束  
   if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {  
   return Result.fail(“秒杀尚未开始！”);  
   }  
   if (voucher.getEndTime().isBefore(LocalDateTime.now())) {  
   return Result.fail(“秒杀尚未结束！”);  
   }  
   // 3.判断库存是否充足  
   if (voucher.getStock() < 1) {  
   return Result.fail(“库存不足！”);  
   }  
   // 4.扣减库存  
   boolean success = seckillVoucherService.update()  
   .setSql(“stock = stock - 1”)  
   .eq(“voucher_id”, voucherId).update();  
   if (!success) {  
   return Result.fail(“库存不足！”);  
   }  
   // 5.创建订单  
   VoucherOrder voucherOrder = new VoucherOrder();  
   long orderId = redisIdWorker.nextId(“order”);  
   voucherOrder.setId(orderId);  
   Long userId = UserHolder.getUser().getId();  
   voucherOrder.setUserId(userId);  
   voucherOrder.setVoucherId(voucherId);  
   save(voucherOrder);  
   return Result.ok(orderId);  
   }
   
   
   ```



### 3.3 超卖问题

#### 3.2.1 问题与解决方案

采用JMeter对秒杀接口进行测试，请求数为200（此处记得在JMeter中设置请求头Token）。发现出现了超卖问题。

假设线程1过来查询库存，判断出来库存大于1，正准备去扣减库存，但是还没有来得及去扣减，此时线程2过来，线程2也去查询库存，发现这个数量一定也大于1，那么这两个线程都会去扣减库存，最终多个线程相当于一起去扣减库存，此时就会出现库存的超卖问题。

解决方案如下：

\-   悲观锁：认为线程安全问题一定会发生，因此在操作数据之前先获取锁，确保线程串行执行。例如Synchronized、Lock都属于悲观锁
\-   乐观锁：认为线程安全问题不一定会发生，因此不加锁，只是在更新数据时去判断有没有其它线程对数据做了修改。如果没有修改则认为是安全的，自己才更新数据。如果已经被其它线程修改说明发生了安全问题，此时可以重试或异常。
    \-   版本号法：给数据加一个version字段。每当数据修改时，version自增1。通过version来判断数据是否被修改。
    \-   CAS法：先比较再修改。在修改时需要判断之前查询到的值与当前的值是否相等，相等才做修改。
\-   悲观锁 vs 乐观锁
    \-   悲观锁实现起来较为简单，但是性能一般，用于插入数据
    \-   乐观锁性能好，但是存在成功率低的问题,用于更新数据

#### 3.2.2 代码实现

乐观锁代码实现：

修改3.2部分代码中的扣减库存内容：**只需要确保当前数据库库存大于0，即可扣减库存。**

```java
// 4.扣减库存
boolean success = seckillVoucherService.update()
    .setSql("stock = stock - 1")
    .eq("voucher_id", voucherId).gt("stock", 0)
    .update();
if (!success) {
    return Result.fail("库存不足！");
}
```

### 3.4 一人一单需求

#### 3.4.1 流程分析

![image-20230228214212184](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202302282142293.png)

同一个优惠券，一个用户只能下一单

添加该需求之后，新的流程为：

秒杀下单流程分析：

1.  提交优惠券ID
2.  查询优惠券信息，判断秒杀是否开始与结束、库存是否充足
3.  根据优惠券ID与用户ID查询订单。若存在，则说明该用户已下过单，返回失败。
4.  扣减库存，创建订单，返回订单ID

#### 3.4.2 代码实现

第一版代码如下：

该代码存在线程并发安全问题，多个线程同时查询，同时执行扣减库存操作，同时创建订单，造成一人一单失败。

```java
@Override
@Transactional
public Result seckillVoucher(Long voucherId) {
    // 1.查询优惠券
    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    // 2.判断秒杀是否开始与结束
    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
        return Result.fail("秒杀尚未开始！");
    }
    if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
        return Result.fail("秒杀尚未结束！");
    }
    // 3.判断库存是否充足
    if (voucher.getStock() < 1) {
        return Result.fail("库存不足！");
    }
    // 4.一人一单，查询订单
    Long userId = UserHolder.getUser().getId();
    int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    if (count > 0) {
        return Result.fail("用户已经购买过一次！");
    }
    // 5.扣减库存
    boolean success = seckillVoucherService.update()
        .setSql("stock = stock - 1")
        .eq("voucher_id", voucherId).gt("stock", 0)
        .update();
    if (!success) {
        return Result.fail("库存不足！");
    }
    // 6.创建订单
    VoucherOrder voucherOrder = new VoucherOrder();
    long orderId = redisIdWorker.nextId("order");
    voucherOrder.setId(orderId);
    Long userId = UserHolder.getUser().getId();
    voucherOrder.setUserId(userId);
    voucherOrder.setVoucherId(voucherId);
    save(voucherOrder);
    return Result.ok(orderId);
}
```

第二版代码：悲观锁

将查询订单、扣减库存、创建订单等代码进行抽取，并添加@Transactional注解，删除原本seckillVoucher方法的事务注解

* **用用户的ID作为Synchronized锁**。由于Long以及toString后都会创建新对象而导致同一个用户的id不是相同的对象，所以应该锁userId.toString().intern()

* **释放锁的操作应该在提交事务之后才执行，因此需要在seckillVoucher中加Synchronized锁，包裹createVoucherOrder方法**

* 非事务调用事务方法，会导致事务失效。因为调用者是this，是当前对象，而不是代理对象。非代理对象不具备事务功能

  1. 添加如下依赖

     ```xml
     <dependency>
         <groupId>org.aspectj</groupId>
         <artifactId>aspectjweaver</artifactId>
     </dependency>
     ```

  2. 在启动类中添加注解：`@EnableAspectJAutoProxy(exposeProxy = true)`

```java
@Override
@Transactional
public Result seckillVoucher(Long voucherId) {
    // 1.查询优惠券
    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    // 2.判断秒杀是否开始与结束
    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
        return Result.fail("秒杀尚未开始！");
    }
    if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
        return Result.fail("秒杀尚未结束！");
    }
    // 3.判断库存是否充足
    if (voucher.getStock() < 1) {
        return Result.fail("库存不足！");
    }
    Long userId = UserHolder.getUser().getId();
    // 为避免每次toString得到新的字符串对象，需要采用intern方法将其添加字符串池
    synchronized (userId.toString().intern()) {
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return proxy.createVoucherOrder(voucherId);
    }
}

@Transactional
public Result createVoucherOrder(Long voucherId) {
    // 4.一人一单，查询订单
    Long userId = UserHolder.getUser().getId();
    int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    if (count > 0) {
        return Result.fail("用户已经购买过一次！");
    }
    // 5.扣减库存
    boolean success = seckillVoucherService.update()
        .setSql("stock = stock - 1")
        .eq("voucher_id", voucherId).gt("stock", 0)
        .update();
    if (!success) {
        return Result.fail("库存不足！");
    }
    // 6.创建订单
    VoucherOrder voucherOrder = new VoucherOrder();
    long orderId = redisIdWorker.nextId("order");
    voucherOrder.setId(orderId);
    voucherOrder.setUserId(userId);
    voucherOrder.setVoucherId(voucherId);
    save(voucherOrder);
    // 7.返回订单ID
    return Result.ok(orderId);
}
```

### 3.5 集群下的线程安全问题

#### 3.5.1 前置准备

1. 复制一个新的启动类 Ctrl+D

   [![](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221101175643840.png)
   
   ](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221101175643840.png "image-20221101175643840")

2. 修改nginx配置文件，实现反向代理和负载均衡

   ```
   location /api {  
       default_type  application/json;
       #internal;  
       keepalive_timeout   30s;  
       keepalive_requests  1000;  
       #支持keep-alive  
       proxy_http_version 1.1;  
       rewrite /api(/.*) $1 break;  
       proxy_pass_request_headers on;
       #more_clear_input_headers Accept-Encoding;  
       proxy_next_upstream error timeout;  
       #proxy_pass http://127.0.0.1:8081;
       proxy_pass http://backend;
   }
   ```

3. 采用 `nginx -s reload` 命令重启nginx

在单机模式下，只有一个JVM，因此采用JVM的同步锁监视器Synchronized可以解决线程安全问题

而在集群模式下，有多个JVM，因此一个JVM的悲观锁对于另外一个JVM来说是不可见的，因此无法解决线程安全问题![image-20230304200427288](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303042004449.png)

4\. 分布式锁
----------------------------------------

### 4.1 介绍

分布式锁：满足分布式系统或集群模式下多进程可见并且互斥的锁。

分布式锁的核心思想：每个服务共用同一把锁，只要大家使用的是同一把锁，那么我们就能锁住线程，不让线程进行，让程序串行执行。

*   可见性：多个线程都能看到相同的结果
    *   注意：这个地方说的可见性并不是并发编程中指的内存可见性，只是说多个进程之间都能感知到变化的意思
*   互斥：互斥是分布式锁的最基本的条件，使得程序串行执行
*   高可用：程序不易崩溃，时时刻刻都保证较高的可用性
*   高性能：由于加锁本身就让性能降低，所有对于分布式锁本身需要他就较高的加锁性能和释放锁性能
*   安全性：安全也是程序中必不可少的一环

### 4.2 实现方案

[![](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303051906265.png)

](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221101180507154.png "image-20221101180507154")

### 4.3 基于Redis的分布式锁

#### 4.3.1 实现思路

1.  获取锁：
    *   采用 `setnx` 命令确保互斥性，采用 `expire` 命令确保超时释放，防止Redis宕机造成锁无法释放的问题。
    *   为确保上述两操作的原子性，可以在同一个 `set` 命令中，执行上述两个操作。
    *   非阻塞：若尝试一次成功，则返回 true；否则返回 false
2.  释放锁：
    *   手动释放，采用 `del` 删除
    *   超时释放

#### 4.3.2 代码实现

##### 第一版代码

1. 在utils包下添加ILock接口

   ```java
   public interface ILock {
       boolean tryLock(long timeoutSec);
       void unlock();
   }
   ```

2. 实现该接口：SimpleRedisLock

   ```java
   public class SimpleRedisLock implements ILock {
   
       private String name;
   
       private StringRedisTemplate template;
   
       private static final String KEY_PREFIX = "lock:";
   
       public SimpleRedisLock(String name, StringRedisTemplate template) {
           this.name = name;
           this.template = template;
       }
   
       @Override
       public boolean tryLock(long timeoutSec) {
           // 获取线程标识
           String threadId = Thread.currentThread().getId();
           // 获取锁
           Boolean success = template.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
           return Boolean.TRUE.equals(success);
       }
   
       @Override
       public void unlock() {
       	// 释放锁
       	template.delete(KEY_PREFIX + name);
       }
   }
   ```

3. 修改VoucherOrderServiceImpl中加锁的逻辑：只对同一个用户做限制（一人一单）

   ```java
   // 创建锁对象
   SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, template);
   // 获取锁
   boolean isLock = lock.tryLock(1200);
   if (!isLock) {
       return Result.fail("不允许重复下单");
   }
   try {
       IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
       return proxy.createVoucherOrder(voucherId);
   } finally {
       lock.unlock();
   }
   ```

##### 防误删

释放锁时，可能出现释放其他线程锁的情况

[![](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303052013310.png)

](https://lty-image-bed.oss-cn-shenzhen.aliyuncs.com/blog/image-20221101181842615.png "image-20221101181842615")

改进思路：

1. 在获取锁时，需要设置该锁对应的值value：用UUID（当前服务对应的唯一ID） + 当前线程ID作为标识。

   防止不同JVM之间造成的线程ID冲突问题

2. 在释放锁时，需要先判断当前线程的标识是否与锁的线程标识一致![image-20230305201446621](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303052014715.png)

代码实现：

```java
public class SimpleRedisLock implements ILock {

    private String name;

    private StringRedisTemplate template;

    private static final String KEY_PREFIX = "lock:";

    // 注意：hutool包下的UUID方法
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate template) {
        this.name = name;
        this.template = template;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = template.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁标识
        String id = template.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(id)) {
            // 释放锁
            template.delete(KEY_PREFIX + name);
        }
    }
}
```

##### Lua脚本解决原子性

判断锁和释放锁操作之间不存在原子性，可能仍会造成误删。![image-20230306123859304](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303061238423.png)

> 由于JVM的垃圾回收机制会阻塞程序运行

代码实现：

1. 在resources下创建 unlock.lua

   ```lua
   -- 比较线程标识与锁中的标识是否一致
   if(redis.call('get', KEYS[1]) == ARGV[1]) then
       return redis.call('del', KEYS[1])
   end
   return 0
   ```

2. 修改SimpleRedisLock：用静态代码块提前读取lua脚本文件

   ```java
   package com.hmdp.utils;
   
   import cn.hutool.core.lang.UUID;
   import org.springframework.core.io.ClassPathResource;
   import org.springframework.data.redis.core.StringRedisTemplate;
   import org.springframework.data.redis.core.script.DefaultRedisScript;
   
   import java.util.Collections;
   import java.util.concurrent.TimeUnit;
   
   public class SimpleRedisLock implements ILock {
   
       private String name;
       private StringRedisTemplate template;
       private static final String KEY_PREFIX = "lock:";
       private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
       private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
   
       static {
           UNLOCK_SCRIPT = new DefaultRedisScript<>();
           UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
           UNLOCK_SCRIPT.setResultType(Long.class);
       }
   
       public SimpleRedisLock(String name, StringRedisTemplate template) {
           this.name = name;
           this.template = template;
       }
   
       @Override
       public boolean tryLock(long timeoutSec) {
           // 获取线程标识
           String threadId = ID_PREFIX + Thread.currentThread().getId();
           // 获取锁
           Boolean success = template.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
           return Boolean.TRUE.equals(success);
       }
   
       @Override
       public void unlock() {
           // 调用lua脚本
           template.execute(
                   UNLOCK_SCRIPT,
                   Collections.singletonList(KEY_PREFIX + name),
                   ID_PREFIX + Thread.currentThread().getId());
       }
   }
   ```

> Lua脚本
>
> 1. Redis 提供的调用函数：redis.call("命令"，"key"，"其他参数")
>
> 2. redis.call('set','name','jack') 执行 set name jack
>
> 3. 调用脚本命令![image-20230306124905104](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303061249168.png)![image-20230306124938674](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303061249740.png)
>
> 4. 如果脚本中的key、value不想写死，可以作为参数传递。key类型参数会放入KEYS数组，其它参数会放入ARGV数组，在脚本中可以从KEYS和ARGV数组获取这些参数（从1开始！！！）
>
>    ![image-20230306125149051](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303061251127.png)

### 4.4 Redisson

![image-20230314125350819](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303141253966.png)

Redisson是一个在Redis的基础上实现的Java驻内存数据网格（In-Memory Data Grid）。它不仅提供了一系列的分布式的Java常用对象，还提供了许多分布式服务，其中就包含了各种分布式锁的实现。

#### 4.4.1 配置

1. 引入依赖

   ```xml
   <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>3.17.1</version>
   </dependency>
   ```

2. 在config下创建RedissonConfig类

   ```java
   @Configuration
   public class RedissonConfig {
       @Bean
       public RedissonClient redissonClient() {
           // 配置
           Config config = new Config();
           config.useSingleServer().setAddress("redis://localhost:6379").setPassword("123456");
           // 创建对象
           return Redisson.create(config);
       }
   }
   ```

3. 修改VoucherOrderServiceImp中创建锁的逻辑

   ```java
   @Autowired
   private RedissonClient redissonClient;
   
   // 创建锁对象
   //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, template);
   RLock lock = redissonClient.getLock("lock:order:" + userId);
   // 获取锁
   boolean isLock = lock.tryLock();
   ```

#### 4.4.2 可重入锁

Redisson采用Redis的哈希结构，key为锁的名称，value为哈希结构：field为线程标识，value为重入次数

加锁解锁流程如下：

1.  加锁：判断锁是否存在
    *   若不存在，则获取锁并添加线程标识，设置锁的有效期，执行业务，进入第2步
    *   若存在，则判断锁标识是否为当前线程
        *   若是，则锁计数加1，并设置锁的有效期，执行业务，进入第2步
        *   若不是，获取锁失败
2.  解锁：判断锁是否是自己的
    *   若是，则锁计数减1。
        *   若锁计数减为0，则释放锁
        *   若锁计数不为0，则重置锁的有效期，继续执行上一层的业务，再进入第2步
    *   若不是，说明锁已被超时释放，逻辑结束

其中加锁与解锁中涉及到多个操作原子性的问题，Redisson用lua脚本实现

#### 4.4.3 锁重试与WatchDog机制

Redisson分布式锁原理：![image-20230314131052538](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303141310648.png)

*   可重入：利用hash结构记录线程id和重入次数
*   可重试：利用信号量和PubSub功能实现等待、唤醒，获取锁失败的重试机制
*   超时续约：利用watchDog，每隔一段时间（releaseTime / 3），重置超时时间

#### 4.4.4 MultiLock

此锁主要用于解决Redis分布式锁主从一致性问题：

采用Redis主从模式：写命令会在主机上执行，读命令会在从机上执行

当主机将数据同步到从机的过程中，主机宕机了，但并没有完成同步数据。当哨兵节点发现主机宕机，并重新选出一个主机时，此时新选出的主机并没有分布式锁的信息，此时便会出现线程安全问题。

为解决此问题，采用MultiLock。每个节点的都是相同的地位，只有当所有的节点都写入成功，才算是加锁成功。假设某个节点宕机，那么便成功完成加锁。

使用：需要创建多个redisClient的Bean![image-20230314194710966](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303141947055.png)

5\. 秒杀优化
----------------------------------------

### 5.1 优化思路

之前秒杀过程如下图所示，tomcat程序中的操作是串行执行。这样会导致较长的执行时间。

![](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303151704165.png)


优化思路为：将耗时比较短的逻辑放入Redis中：判断库存是否充足、判断是否为一人一单，这两个判断是业务的核心逻辑，判断正确无误意味着一定可以完成下单，便可返回订单ID。而耗时较长的逻辑：创建订单、减库存交由另外一个线程去处理，主线程只需要将与秒杀相关的优惠券ID、用户ID、订单ID保存到消息队列，让另外一个线程从队列中读取，并完成剩余的逻辑即可。

其中一人一单通过Redis中的set集合来完成，key为订单ID，value为set集合，里面存储用户ID。

新的流程为：

*   对于主线程：
    1.  从Redis中判断订单是否充足、判断是否满足一人一单
    2.  满足条件，则扣减Redis中的库存信息，将用户ID存入对应的set集合。此部分采用lua脚本以确保原子性
    3.  将相关信息添加到阻塞队列中
    4.  返回订单ID
*   对于另外开辟的线程：
    1.  从阻塞队列中获取优惠券ID、用户ID、订单ID等信息
    2.  将订单信息添加到数据库中，并扣减数据库中的库存

优化秒杀过程如下图所示

![](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303151705193.png)

### 5.2 代码实现

1. 修改VoucherServiceImpl中添加秒杀优惠券的方法addSeckillVoucher

   在添加的过程中将库存保存到Redis

   ```java
   @Override
   @Transactional
   public void addSeckillVoucher(Voucher voucher) {
       // 保存优惠券
       save(voucher);
       // 保存秒杀信息
       SeckillVoucher seckillVoucher = new SeckillVoucher();
       seckillVoucher.setVoucherId(voucher.getId());
       seckillVoucher.setStock(voucher.getStock());
       seckillVoucher.setBeginTime(voucher.getBeginTime());
       seckillVoucher.setEndTime(voucher.getEndTime());
       seckillVoucherService.save(seckillVoucher);
       // 保存秒杀库存到Redis
       template.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
   }
   ```

2. 用lua脚本实现Redis中查询库存、判断一人一单、减库存等操作

   ```lua
   -- 1.参数列表
   -- 1.1.优惠券id
   local voucherId = ARGV[1]
   -- 1.2.用户id
   local userId = ARGV[2]
   -- 2.数据key
   -- 2.1.库存key
   local stockKey = 'seckill:stock:' .. voucherId
   -- 2.2.订单key
   local orderKey = 'seckill:order:' .. voucherId
   
   -- 3.脚本业务
   -- 3.1.判断库存是否充足 get stockKey
   if(tonumber(redis.call('get', stockKey)) <= 0) then
       -- 3.2.库存不足，返回1
       return 1
   end
   -- 3.2.判断用户是否下单 SISMEMBER orderKey userId
   if(redis.call('sismember', orderKey, userId) == 1) then
       -- 3.3.存在，说明是重复下单，返回2
       return 2
   end
   -- 3.4.扣库存 incrby stockKey -1
   redis.call('incrby', stockKey, -1)
   -- 3.5.下单（保存用户）sadd orderKey userId
   redis.call('sadd', orderKey, userId)
   return 0
   ```

3. 修改VoucherOrderServiceImpl中的seckillVoucher方法

   由于proxy在另外一个线程中也需要用到，所以将其提到外面。

   ```java
   private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
   
   static {
       SECKILL_SCRIPT = new DefaultRedisScript<>();
       SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
       SECKILL_SCRIPT.setResultType(Long.class);
   }
   
   private IVoucherOrderService proxy;
   
   @Override
   public Result seckillVoucher(Long voucherId) {
       Long userId = UserHolder.getUser().getId();
       // 1.执行lua脚本
       Long result = template.execute(
           SECKILL_SCRIPT,
           Collections.emptyList(),
           voucherId.toString(), userId.toString()
       );
       // 2.判断结果是否为0
       int r = result.intValue();
       // 2.1 不为0，代表没有购买资格
       if (r != 0) {
           return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
       }
       // 2.2 为0，有购买资格，把下单信息保存到阻塞队列
       VoucherOrder voucherOrder = new VoucherOrder();
       long orderId = redisIdWorker.nextId("order");
       voucherOrder.setId(orderId);
       voucherOrder.setUserId(userId);
       voucherOrder.setVoucherId(voucherId);
       // 2.3 放入阻塞队列
       orderTasks.add(voucherOrder);
       // 3.获取代理对象
       proxy = (IVoucherOrderService) AopContext.currentProxy();
       // 4.返回订单id
       return Result.ok(orderId);
   }
   ```

4. 添加阻塞队列处理的逻辑，实现异步在数据库中完成下单操作

   ```java
   // 阻塞队列，存放相关订单信息
   private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
   // 异步执行线程池
   private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
   // 在类初始化之前执行线程池任务
   @PostConstruct
   private void init() {
       SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
   }
   
   private class VoucherOrderHandler implements Runnable {
       @Override
       public void run() {
           while (true) {
               try {
                   // 1.获取队列中的订单信息
                   VoucherOrder voucherOrder = orderTasks.take();
                   // 2.创建订单
                   handleVoucherOrder(voucherOrder);
               } catch (InterruptedException e) {
                   log.error("处理订单异常", e);
               }
           }
       }
   }
   
   private void handleVoucherOrder(VoucherOrder voucherOrder) {
       Long userId = voucherOrder.getUserId();
       // 创建锁对象
       //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, template);
       RLock lock = redissonClient.getLock("lock:order:" + userId);
       // 获取锁
       boolean isLock = lock.tryLock();
       if (!isLock) {
           log.error("不允许重复下单");
           return;
       }
       try {
           proxy.createVoucherOrder(voucherOrder);
       } finally {
           lock.unlock();
       }
   }
   ```

6\. Redis消息队列
------------------------------------------------------------

消息队列（Message Queue），字面意思就是存放消息的队列。最简单的消息队列模型包括3个角色：

*   消息队列：存储和管理消息，也被称为消息代理（Message Broker）
*   生产者：发送消息到消息队列
*   消费者：从消息队列获取消息并处理消息![image-20230315230618418](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303152306540.png)

Redis提供了三种不同的方式来实现消息队列：

*   list结构：基于List结构模拟消息队列
*   PubSub：基本的点对点消息模型
*   Stream：比较完善的消息队列模型

### 6.1 基于List的消息队列

Redis的list数据结构是一个双向链表，利用：LPUSH 结合 RPOP、或者 RPUSH 结合 LPOP实现。

当队列中没有消息时RPOP或LPOP操作会返回null，并不像JVM的阻塞队列那样会阻塞并等待消息。因此这里应该使用BRPOP或者BLPOP来实现阻塞效果。

优点：

*   利用Redis存储，不受限于JVM内存上限
*   基于Redis的持久化机制，数据安全性有保证
*   可以满足消息有序性

缺点：

*   无法避免消息丢失
*   只支持单消费者

### 6.2 基于PubSub的消息队列

![image-20230315231515652](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303152315737.png)

PubSub（发布订阅）是Redis2.0版本引入的消息传递模型。

*   消费者可以订阅一个或多个channel，生产者向对应channel发送消息后，所有订阅者都能收到相关消息。
    *   SUBSCRIBE channel \[channel\] ：订阅一个或多个频道
    *   PUBLISH channel msg ：向一个频道发送消息
    *   PSUBSCRIBE pattern\[pattern\] ：订阅与pattern格式匹配的所有频道

优点：采用发布订阅模型，支持多生产、多消费

缺点：

*   不支持数据持久化
*   无法避免消息丢失（没人监听时会丢失）
*   消息堆积有上限，超出时数据丢失



### 6.3基于Stream的消息队列

Stream是Redis5.0引入的一种新数据类型，可以实现一个功能非常完善的消息队列。

#### 发布消息

![image-20230315233104956](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303152331052.png)

例子![image-20230315233122584](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303152331657.png)

#### 读取消息

![image-20230315233930501](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303152339611.png)

> 当我们指定起始ID为$时，代表读取最新的消息，如果我们处理一条消息的过程中，又有超过1条以上的消息到达队列，则下次获取时也只能获取到最新的一条，会出现漏读消息的问题。

#### 特点

* 消息可回溯
* 一个消息可以被多个消费者读取
* 可以阻塞读取
* 有消息漏读的风险



### 6.4基于Stream的消息队列-消费者组

将多个消费者划分到一个组中，监听同一个队列。具备下列特点：

![image-20230315234857450](C:/Users/wangy/AppData/Roaming/Typora/typora-user-images/image-20230315234857450.png)

#### 基础命令

创建![image-20230315235938393](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303160002585.png)

* key:队列名称
* groupName:消费者组名称
* ID:起始ID标示，$代表队列中最后一个消息，0则代表队列中第一个消息
* KSTREAM:队列不存在时自动创建队列

读取![image-20230316000330777](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303160003852.png)

* group:消费组名称
* consumer:消费者名称，如果消费者不存在，会自动创建一个消费者
* count:本次查询的最大数量
* BLOCK milliseconds:当没有消息时最长等待时间

其他

![image-20230316000015986](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303160002310.png)

7\. 达人探店
----------------------------------------

### 7.1 发布探店笔记

笔记由图片与文字构成，因此需要两个接口：上传图片接口、发布笔记接口。先上传图片，然后点击发布按钮，完成发布。

上传图片接口：其中需要注意的是，需要修改SystemConstants类下的IMAGE\_UPLOAD\_DIR，修改为自己本地nginx或者云存储位置。

```java
@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

}
```

BlogController：完成发布笔记

```java
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        //获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUpdateTime(user.getId());
        //保存探店博文
        blogService.saveBlog(blog);
        //返回id
        return Result.ok(blog.getId());
    }
}
```

### 7.2 查看探店笔记

BlogServiceImpl

```java
@Override
public Result queryBlogById(Long id) {
    // 1.查询blog
    Blog blog = getById(id);
    if (blog == null) {
        return Result.fail("笔记不存在！");
    }
    // 2.查询blog有关的用户
    queryBlogUser(blog);
    return Result.ok(blog);
}
```

### 7.3 点赞功能

初始时点赞代码位于BlogController的queryBlogLikes接口

```java
@GetMapping("/likes/{id}")
public Result queryBlogLikes(@PathVariable("id") Long id) {
    //修改点赞数量
    blogService.update().setSql("liked = liked +1 ").eq("id",id).update();
    return Result.ok();
}
```

但是该代码会导致一个用户可以无限地为一篇笔记点赞，显然不符合实际的业务需求。

需求如下：

1.  同一个用户只能点赞一次，再次点击则取消点赞
2.  如果当前用户已点赞，那么点赞按钮需要高亮显示

实现步骤：

1.  给Blog类中添加一个isLike字段，标示是否被当前用户点赞
2.  修改点赞功能，利用Redis的set集合判断是否点赞过，未点赞过则点赞数+1，已点赞过则点赞数-1
    *   采用set集合可以对点赞用户进行去重，已点赞的用户存在于某笔记对应的set集合中，则不能再次点赞
3.  修改根据id查询Blog的业务，判断当前登录用户是否点赞过，赋值给isLike字段
4.  修改分页查询Blog业务，判断当前登录用户是否点赞过，赋值给isLike字段

代码实现：

修改BlogController对应的likeBlog接口方法，并重写该方法。具体逻辑见注释。

```java
@PutMapping("/like/{id}")
public Result likeBlog(@PathVariable("id") Long id) {
    // 修改点赞数量
    return blogService.likeBlog(id);
}
```

```java
@Override
public Result likeBlog(Long id) {
    // 1.获取登录用户
    Long userId = UserHolder.getUser().getId();
    // 2.判断当前登录用户是否点赞
    String key = BLOG_LIKED_KEY + id;
    Boolean isMember = template.opsForSet().isMember(key, userId.toString());
    if (BooleanUtil.isFalse(isMember)) {
        // 3.如果未点赞
        // 3.1 数据库点赞数+1
        boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
        // 3.2 保存用户到Redis的set集合中
        if (isSuccess) {
            template.opsForSet().add(key, userId.toString());
        }
    } else {
        // 4.如果已点赞
        // 4.1 数据库点赞数-1
        boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
        // 4.2 把用户从Redis的set集合中移除
        if (isSuccess) {
            template.opsForSet().remove(key, userId.toString());
        }
    }
    return Result.ok();
}
```

### 7.4 点赞排行榜

功能需求为：在笔记的详情页面，将最先为笔记点赞的前N个人显示出来。

为满足此功能，我们需要统计每个人为笔记点赞的时间，然后按照该时间将set集合从小到大排序，取出前N个人。

Redis中的sortedSet可以满足此需求，用时间戳作为其的score属性，可完成时间排序。

代码实现：

1. 修改点赞的逻辑，即likeBlog方法

   ```java
   @Override
   public Result likeBlog(Long id) {
       // 1.获取登录用户
       Long userId = UserHolder.getUser().getId();
       // 2.判断当前登录用户是否点赞
       String key = BLOG_LIKED_KEY + id;
       Double score = template.opsForZSet().score(key, userId.toString());
       if (score == null) {
           // 3.如果未点赞
           // 3.1 数据库点赞数+1
           boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
           // 3.2 保存用户到Redis的zset集合中，根据点赞时间排序
           if (isSuccess) {
               template.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
           }
       } else {
           // 4.如果已点赞
           // 4.1 数据库点赞数-1
           boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
           // 4.2 把用户从Redis的zset集合中移除
           if (isSuccess) {
               template.opsForZSet().remove(key, userId.toString());
           }
       }
       return Result.ok();
   }
   ```

2. 修改点赞列表查询的接口方法：queryBlogLikes

   ```java
   @GetMapping("/likes/{id}")
   public Result queryBlogLikes(@PathVariable("id") Long id) {
       return blogService.queryBlogLikes(id);
   }
   ```

   ```java
   @Override
   public Result queryBlogLikes(Long id) {
       String key = BLOG_LIKED_KEY + id;
       // 1.查询top5点赞用户
       Set<String> top5 = template.opsForZSet().range(key, 0, 4);
       if (top5 == null || top5.isEmpty()) {
           return Result.ok(Collections.emptyList());
       }
       // 2.解析出用户id
       List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
       String idStrs = StrUtil.join(",", ids);
       // 3.根据用户id查询用户
       List<UserDTO> users = userService.query()
           .in("id", ids)
           .last("ORDER BY FIELD(id," + idStrs + ")").list()
           .stream()
           .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
           .collect(Collectors.toList());
       // 4.返回
       return Result.ok(users);
   }
   ```

8\. 好友关注
----------------------------------------

### 8.1 关注与取关

关注与被关注是存在于所有用户之间的，因此用一张额外的表 tb_follow 记录这一关系。

需要编写两个接口：关注取关、判断是否关注

代码实现：

FollowController，重写follow与isFollow方法

```java
//关注
@PutMapping("/{id}/{isFollow}")
public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
    return followService.follow(followUserId, isFollow);
}
//取消关注
@GetMapping("/or/not/{id}")
public Result isFollow(@PathVariable("id") Long followUserId) {
      return followService.isFollow(followUserId);
}
```

```java
@Override
public Result follow(Long followUserId, Boolean isFollow) {
    Long userId = UserHolder.getUser().getId();
    if (isFollow) {
        // 1.关注则新增数据
        Follow follow = new Follow();
        follow.setFollowUserId(followUserId);
        follow.setUserId(userId);
        save(follow);
    } else {
        // 2.取关则删除数据
        remove(new QueryWrapper<Follow>()
               .eq("user_id", userId).eq("follow_user_id", followUserId));
    }
    return Result.ok();
}

@Override
public Result isFollow(Long followUserId) {
    Long userId = UserHolder.getUser().getId();
    // 查询是否关注
    Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
    return Result.ok(count > 0);
}
```

### 8.2 共同关注

共同关注具体为：当前用户查看另外一个用户的主页时，可以查看共同关注，即当前用户与所查看用户的共同关注用户列表

通过set集合实现共同关注的功能：当调用follow接口关注某人时，可以将被关注的用户放入当前用户对应的一个set集合中，该set集合存储着所有被当前用户关注过的用户。

代码实现：

1. 修改follow接口方法

   ```java
   @Override
   public Result follow(Long followUserId, Boolean isFollow) {
       Long userId = UserHolder.getUser().getId();
       String key = "follows:" + userId;
       if (isFollow) {
           // 1.关注则新增数据
           Follow follow = new Follow();
           follow.setFollowUserId(followUserId);
           follow.setUserId(userId);
           boolean save = save(follow);
           if (save) {
               // 将关注用户的id放入redis的set集合中
               template.opsForSet().add(key, followUserId.toString());
           }
       } else {
           // 2.取关则删除数据
           boolean remove = remove(new QueryWrapper<Follow>()
                                   .eq("user_id", userId).eq("follow_user_id", followUserId));
           if (remove) {
               // 将关注用户的id从redis的set集合中移除
               template.opsForSet().remove(key, followUserId.toString());
           }
       }
       return Result.ok();
   }
   ```

2. 查看共同关注，实现followCommons方法

   ```java
   @GetMapping("/common/{id}")
   public Result followCommons(@PathVariable("id") Long id) {
       return followService.followCommons(id);
   }
   ```

   ```java
   @Override
   public Result followCommons(Long id) {
       // 1.获取当前登录用户
       Long userId = UserHolder.getUser().getId();
       String key = "follows:" + userId;
       String key2 = "follows:" + id;
       // 2.求交集
       Set<String> intersect = template.opsForSet().intersect(key, key2);
       if (intersect == null || intersect.isEmpty()) {
           return Result.ok(Collections.emptyList());
       }
       // 3.解析id集合
       List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
       // 4.查询用户
       List<UserDTO> users = userService.listByIds(ids)
           .stream()
           .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
           .collect(Collectors.toList());
       return Result.ok(users);
   }
   ```

## 9.Feed流实现关注推送

### 9.1关注推送

关注推送也叫做Feed流，直译为投喂。为用户持续的提供“沉浸式”的体验，通过无限下拉刷新获取新的信息![image-20230320231610945](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303202316061.png)

Feed流产品有两种常见模式：

* Timeline:不做内容筛选，简单的按照内容发布时间排序，常用于好友或关注。例如朋友圈

  * 优点：信息全面，不会有缺失。并且实现也相对简单
  * 缺点：信息噪音较多，用户不一定感兴趣，内容获取效率低

* 智能排序：利用智能算法屏蔽掉违规的、用户不感兴趣的内容。推送用户感兴趣信息来吸引用户

  * 优点：投喂用户感兴趣信息，用户粘度很高，容易沉迷
  * 缺点：如果算法不精准，可能起到反作用

  实现方式：

  ![image-20230320232452876](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303202324986.png)



#### 9.2基于推模式实现关注推送

##### 需求

1. 修改新增探店笔记的业务，在保存bog到数据库的同时，推送到粉丝的收件箱
2. 收件箱满足可以根据时间戳排序，必须用Redis的数据结构实现
3. 查询收件箱数据时，可以实现分页查询

##### Feed流的分页问题

Feed流中的数据会不断更新，所以数据的角标也在变化，因此不能采用传统的分页模式。

<img src="https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303202332308.png" alt="image-20230320233217204" style="zoom: 50%;" />

*滚动分页*：采用记录上次查询的最后id可以解决这个问题

<img src="https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303202334066.png" alt="image-20230320233446974" style="zoom: 50%;" />

> 注：list 只能用角标查询无法实现滚动分页功能，而sortedSet可以根据指定的score值来查询

##### 命令

ZREVRANGEBYSCORE max min offset count

> 参数说明
>
> max：当前时间戳 || 上次查询的最小时间戳
>
> min：0
>
> offset：0 || 在上一次查询中与最小元素时间戳相同的个数
>
> count：一页查询的个数
>
> ==注:这样才可以防止多个时间提交的关注推送不会重复==



## 10.GEO数据结构

### 10.1介绍

GEO就是Geolocation的简写形式，代表地理坐标。Redis在3.2版本中加入了对GEO的支持，允许存储地理坐标信息，帮助我们根据经纬度来检索数据。常见的命令有：

* **GEOADD**:添加一个地理空间信息，包含：经度(longitude)、纬度(latitude)、值(member)
* **GEODIST**：计算指定的两个点之间的距离并返回
* **GEOHASH**:将指定member的坐标转为hash字符串形式并返回
* **GEOPOS**:：返回指定member的坐标
* **GEOSEARCH**:在指定范围内搜索member,并按照与指定点之间的距离排序后返回。范围可以是圆形或矩形。6.2.新功能
* **GEOSEARCHSTORE**：与GEOSEARCH功能一致，不过可以把结果存储到一个指定的key。6.2.新功能

#### 10.2附近商户搜索

 

```java
public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // Determine whether you need to query based on coordinate
        if (x == null || y == null) {
            // Query by type
            Page<Shop> page = query().eq("type_id", typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // Calculate pagination parameters
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // Query redis, sort by distance, pagination
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        // Resolve the ID
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (results == null || content.size() == 0) {
            return Result.ok(Collections.emptyList());
        }
        // There is no next page, over
        if (content.size() <= start) {
            return Result.ok(Collections.emptyList());
        }

        ArrayList<Object> ids = new ArrayList<>(content.size());
        HashMap<String, Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(start).forEach(result -> {
            // Get the ShopID
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // Get the distance
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // Search for stores by ID
        String join = StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids)
                .last("order by field(id," + join + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
```

## 11.BitMap

### 11.1引入

![image-20230322130612794](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303221306891.png)

假如有1000万用户，平均每人每年签到次数为10次，则这张表一年的数据量为1亿条。每签到一次需要使用（8+8+1+1+3+1)共22字节的内存，一个月则最多需要600多字节

### 11.2BitMap用法

我们按月来统计用户签到信息，签到记录为1，未签到则记录为0。把每一个bit位对应当月的每一天，形成了映射关系。用0和1标示业务状态，这种思路就称为位图(BitMap)。![image-20230322131013899](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303221310969.png)

Redis中是利用string类型数据结构实现BitMap,因此最大上限是512M,转换为bit则是2^32个bit位。
BitMap的操作命令有：

* **SETBIT**:向指定位置(offset)存入一个0或1
* **GETBIT**:获取指定位置(offset)的bit值。
* **BITCOUNT**：统计BitMap中值为1的bit位的数量
* **BITFIELD**:操作（查询、修改、自增)BitMap中bit数组中的指定位置(offset)的值
* **BITFIELD_RQ**:获取BitMap中bit数组，并以十进制形式返回
* **BTOP**：将多个BitMap的结果做位运算（与、或、异或)
* **BITPOS**:查找bit数组中指定范围内第一个O或1出现的位置

> 因为BitMap底层是基于String数据结构，因此其操作都封装在字符串相关操作中了
>
> ![image-20230322164811570](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303221648660.png)



### 11.3 用户签到

```java
public Result sign() {
        // Gets the currently logged-on user
        Long userId = UserHolder.getUser().getId();
        // Get the date
        LocalDateTime now = LocalDateTime.now();
        // Splicing key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = "sign:" + userId + keySuffix;
        // Get today is the day of the month
        int day = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }
```

 

### 11.4 签到统计

#### 11.4.1问题

**如何获取到本月到今天为止的所有签到数据**

```
BITFIELD key GET u[dayOfMonth] 0
```

**如何从后向前遍历每个bit位**

与1做与运算，就能得到最后一个bit位。随后右移1位，下一个bit位就成为了最后一个bit位。



#### 11.4.1 代码实现

```java
public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        // Get the date
        LocalDateTime now = LocalDateTime.now();
        // Splicing key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = "sign:" + userId + keySuffix;
        // Get today is the day of the month
        int day = now.getDayOfMonth();
        // Get all check-ins up to date this month
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // Loop traversal
        int count = 0;
        while (true){
            if ((num & 1) == 0){
                break;
            }else {
                count++;
            }
            num = num >>> 1;
        }
        return Result.ok(count);
    }
```



## 12.HyperLogLog

### 12.1 介绍

首先我们搞懂两个概念：

* UV:全称Unique Visitor,也叫独立访客量，是指通过互联网访问、浏览这个网页的自然人。1天内同一个用户多次访问该网站，只记录1次。

* PV:全称Page View,也叫页面访问量或点击量，用户每访问网站的一个页面，记录1次PV,用户多次打开页面，则记录多次PV。往往用来衡量网站的流量。

  UV统计在服务端做会比较麻烦，因为要判断该用户是否已经统计过了，需要将统计过的用户信息保存。但是如果每个访问的用户都保存到Redis中，数据量会非常恐怖。



### 12.2.用法

Hyperloglog(HLL)是从Loglog算法派生的概率算法，用于确定非常大的集合的基数，而不需要存储其所有值。Redist中的HLL是基于string结构实现的，单个HLL的内存永远小于16kb,内存占用低的令人发指！作为代价，其测量结果是概率性的，有小于0.81%的误差。不过对于UV统计来说，这完全可以忽略。

![image-20230322174550565](https://cdn.jsdelivr.net/gh/KingRainIce/typora-pic@main/202303221745721.png)
