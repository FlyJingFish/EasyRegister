package com.flyjingfish.androidaop

import android.util.Log
import com.flyjingfish.android_aop_annotation.ProceedJoinPoint
import com.flyjingfish.android_aop_annotation.anno.AndroidAopMatchClassMethod
import com.flyjingfish.android_aop_annotation.base.MatchClassMethod
import com.flyjingfish.android_aop_annotation.enums.MatchType
import com.flyjingfish.test_lib.ToastUtils

@AndroidAopMatchClassMethod(
    targetClassName = "com.flyjingfish.androidaop.test.Base1",
    methodName = ["getTest"],
    type = MatchType.EXTENDS
)
class MatchRound : MatchClassMethod {
    override fun invoke(joinPoint: ProceedJoinPoint, methodName: String): Any? {
        Log.e("MatchRound", "======$methodName");
        ToastUtils.makeText(ToastUtils.app,"MatchRound======$methodName")
        return joinPoint.proceed()
    }
}