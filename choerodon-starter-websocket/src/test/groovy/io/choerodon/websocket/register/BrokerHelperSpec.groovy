package io.choerodon.websocket.register


import io.choerodon.websocket.helper.BrokerHelper
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import spock.lang.Specification

import java.util.concurrent.ScheduledExecutorService

/**
 * @author dengyouquan
 * */
class BrokerHelperSpec extends Specification {
    private Environment environment = Mock(Environment)
    private StringRedisTemplate redisTemplate = Mock(StringRedisTemplate)
    //需要执行线程池中的方法，用Spy
    private ScheduledExecutorService scheduledExecutorService = Spy(ScheduledExecutorService)
    private BrokerHelper brokerHelper

    def setup(){
        brokerHelper = new BrokerHelper(environment, redisTemplate)
    }

    def "RemoveDeathChannel"() {
        given: "构造请求参数"
        SetOperations<String, Object> setOperations = Mock(SetOperations)
        when: "调用方法"
        brokerHelper.removeDeathBroker("channel")
        then: "校验结果"
        1 * redisTemplate.opsForSet() >> { setOperations }
        1 * setOperations.remove(_, _)
        2 * redisTemplate.delete(_)
    }
}
