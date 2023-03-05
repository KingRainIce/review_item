package com.ice.learning.review_pro.utils;

/**
 * @Title: ILock
 * @Auth: Ice
 * @Date: 2023/3/5 19:08
 * @Version: 1.0
 * @Desc:
 */
public interface ILock {

    boolean tryLock(long timeoutSec);

    void unLock();

}
