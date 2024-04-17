package oppdrag.mq

import com.ibm.mq.constants.CMQC
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import oppdrag.MQConfig
import javax.jms.Connection
import javax.jms.ExceptionListener
import javax.jms.MessageListener

interface MQConsumer : MessageListener, ExceptionListener, AutoCloseable {
    fun start()
}

interface MQProducer {
    fun send(xml: String, con: Connection)
}

object MQFactory {
    fun new(config: MQConfig): MQConnectionFactory =
        MQConnectionFactory().apply {
            hostName = config.host
            port = config.port
            queueManager = config.manager
            channel = config.channel
            transportType = WMQConstants.WMQ_CM_CLIENT
            ccsid = JmsConstants.CCSID_UTF8
            setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
            setIntProperty(JmsConstants.JMS_IBM_ENCODING, CMQC.MQENC_NATIVE)
            setIntProperty(JmsConstants.JMS_IBM_CHARACTER_SET, JmsConstants.CCSID_UTF8)
        }
}