/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
  * by the @authors tag. See the copyright.txt in the distribution for a
  * full listing of individual contributors.
  *
  * This is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as
  * published by the Free Software Foundation; either version 2.1 of
  * the License, or (at your option) any later version.
  *
  * This software is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  * Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public
  * License along with this software; if not, write to the Free
  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
  */
package org.jboss.test.messaging.jms;

import java.util.Map;

import javax.naming.InitialContext;
import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Connection;
import javax.jms.MapMessage;
import javax.jms.Session;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.message.JBossMapMessage;
import org.jboss.jms.message.JBossMessage;
import org.jboss.jms.message.MessageDelegate;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.tx.TransactionManagerImpl;
import org.jboss.util.id.GUID;

/**
 * @author <a href="mailto:tim.l.fox@gmail.com">Tim Fox</a>
 *
 */
public class MessageDelegateTest extends MessagingTestCase
{
   // Constants -----------------------------------------------------
   
   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------
   
   protected InitialContext initialContext;
   
   protected JBossConnectionFactory cf;
   protected Destination queue;
   
   // Constructors --------------------------------------------------
   
   public MessageDelegateTest(String name)
   {
      super(name);
   }
   
   // TestCase overrides -------------------------------------------
   
   public void setUp() throws Exception
   {
      super.setUp();
      ServerManagement.init("all");
      initialContext = new InitialContext(ServerManagement.getJNDIEnvironment());
      cf = (JBossConnectionFactory)initialContext.lookup("/ConnectionFactory");
      
      ServerManagement.undeployQueue("Queue");
      ServerManagement.deployQueue("Queue");
      queue = (Destination)initialContext.lookup("/queue/Queue");
      
      log.debug("setup done");
   }
   
   public void tearDown() throws Exception
   {
      super.tearDown();
   }
   
   
   // Public --------------------------------------------------------
         
   public void testCopyAfterSend() throws Exception
   {
      if (ServerManagement.isRemote())
      {
         return;
      }
      
      Connection conn = null;
      
      try
      {
         conn = cf.createConnection();
         
         conn.start();
         
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageProducer prod = sess.createProducer(queue);
         
         MessageConsumer cons = sess.createConsumer(queue);
         
         MapMessage msent = sess.createMapMessage();
         msent.setString("map_entry", "map_value");         
         msent.setStringProperty("property_entry", "property_value");
         
         prod.send(msent);
         
         MapMessage mrec = (MapMessage)cons.receive();
                  
         //Underlying messages
         JBossMessage usent_1 = ((MessageDelegate)msent).getMessage();         
         JBossMessage urec_1 = ((MessageDelegate)mrec).getMessage();
         
         //The underlying message should be the same since we haven't changed it after sending or receiving
         assertTrue(usent_1 == urec_1);
         
         //Now change a header in the sent message
         //The should cause the underlying message to be copied
         msent.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);
         
         JBossMessage usent_2 = ((MessageDelegate)msent).getMessage();
         JBossMessage urec_2 = ((MessageDelegate)mrec).getMessage();
         
         assertFalse(usent_2 == usent_1);
         assertTrue(usent_1 == urec_2);
         assertTrue(urec_1 == urec_2);
                  
         //But the properties shouldn't be copied since we didn't change them         
         assertTrue(usent_2.getJMSProperties() == usent_1.getJMSProperties());
         assertTrue(urec_2.getJMSProperties() == urec_1.getJMSProperties());
         assertTrue(usent_1.getJMSProperties() == urec_1.getJMSProperties());
         

         //And the bodies shouldn't be copied since we didn't change it either
         assertTrue(usent_2.getPayload() == usent_1.getPayload());
         assertTrue(urec_2.getPayload() == urec_1.getPayload());
         assertTrue(usent_1.getPayload() == urec_1.getPayload());
         
         //Now we change a property
         msent.setIntProperty("my_int_prop", 123);
         
         JBossMessage usent_3 = ((MessageDelegate)msent).getMessage();
         JBossMessage urec_3 = ((MessageDelegate)mrec).getMessage();
                  
         //It shouldn't cause a copy of the whole message again
         assertTrue(usent_3 == usent_2);
         assertTrue(urec_3 == urec_2);
         
         //But the properties should be copied in the sent message but not the received
         
         Map sentProps = usent_3.getJMSProperties();
         Map recProps = urec_3.getJMSProperties();
         
         assertFalse (sentProps == usent_1.getJMSProperties());
         assertTrue (recProps == urec_1.getJMSProperties());
         
         
         
         //Body should be the same
         assertTrue(usent_3.getPayload() == usent_1.getPayload());
         assertTrue(urec_3.getPayload() == urec_1.getPayload());
         
         //Now we change the body
         msent.setString("new_map_prop", "hello");
         
         JBossMessage usent_4 = ((MessageDelegate)msent).getMessage();
         JBossMessage urec_4 = ((MessageDelegate)mrec).getMessage();
         
         //It shouldn't cause a copy of the whole message again
         assertTrue(usent_4 == usent_3);
         assertTrue(urec_4 == urec_3);
         
         //The properties should not be copied again
         assertTrue (usent_4.getJMSProperties() == sentProps);
         assertTrue (urec_4.getJMSProperties() == recProps);
         
         //Body should be copied in the sent but not the received
         assertFalse(usent_4.getPayload() == usent_1.getPayload());
         assertTrue(urec_4.getPayload() == urec_1.getPayload());
                       
      }
      finally
      {      
         if (conn != null)
         {
            conn.close();
         }
      }
      
   }
   
   public void testCopyAfterReceive() throws Exception
   {
      if (ServerManagement.isRemote())
      {
         return;
      }
      
      Connection conn = null;
      
      try
      {
         conn = cf.createConnection();
         
         conn.start();
         
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         MessageProducer prod = sess.createProducer(queue);
         
         MessageConsumer cons = sess.createConsumer(queue);
         
         MapMessage msent = sess.createMapMessage();
         msent.setString("map_entry", "map_value");         
         msent.setStringProperty("property_entry", "property_value");
         
         prod.send(msent);
         
         MapMessage mrec = (MapMessage)cons.receive();
                  
         //Underlying messages
         JBossMessage usent_1 = ((MessageDelegate)msent).getMessage();         
         JBossMessage urec_1 = ((MessageDelegate)mrec).getMessage();
         
         //The underlying message should be the same since we haven't changed it after sending or receiving
         assertTrue(usent_1 == urec_1);
         
         //Now change a header in the received message
         //The should cause the underlying message to be copied
         mrec.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);
         
         JBossMessage usent_2 = ((MessageDelegate)msent).getMessage();
         JBossMessage urec_2 = ((MessageDelegate)mrec).getMessage();
         
         assertTrue(usent_2 == usent_1);
         assertFalse(urec_1 == urec_2);
                  
         //But the properties shouldn't be copied since we didn't change them         
         assertTrue(usent_2.getJMSProperties() == usent_1.getJMSProperties());
         assertTrue(urec_2.getJMSProperties() == urec_1.getJMSProperties());
         assertTrue(usent_1.getJMSProperties() == urec_1.getJMSProperties());
         

         //And the bodies shouldn't be copied since we didn't change it either
         assertTrue(usent_2.getPayload() == usent_1.getPayload());
         assertTrue(urec_2.getPayload() == urec_1.getPayload());
         assertTrue(usent_1.getPayload() == urec_1.getPayload());
         
         //Now we change a property
         mrec.clearProperties();
         mrec.setIntProperty("my_int_prop", 123);
         
         JBossMessage usent_3 = ((MessageDelegate)msent).getMessage();
         JBossMessage urec_3 = ((MessageDelegate)mrec).getMessage();
                  
         //It shouldn't cause a copy of the whole message again
         assertTrue(usent_3 == usent_2);
         assertTrue(urec_3 == urec_2);
         
         //But the properties should be copied in the received message but not the sent
         
         Map sentProps = usent_3.getJMSProperties();
         Map recProps = urec_3.getJMSProperties();
         
         assertTrue (sentProps == usent_1.getJMSProperties());
         assertFalse (recProps == urec_1.getJMSProperties());
         
         //Body should be the same
         assertTrue(usent_3.getPayload() == usent_1.getPayload());
         assertTrue(urec_3.getPayload() == urec_1.getPayload());
         
         //Now we change the body
         mrec.clearBody();
         mrec.setString("new_map_prop", "hello");
         
         JBossMessage usent_4 = ((MessageDelegate)msent).getMessage();
         JBossMessage urec_4 = ((MessageDelegate)mrec).getMessage();
         
         //It shouldn't cause a copy of the whole message again
         assertTrue(usent_4 == usent_3);
         assertTrue(urec_4 == urec_3);
         
         //The properties should not be copied again
         assertTrue (usent_4.getJMSProperties() == sentProps);
         assertTrue (urec_4.getJMSProperties() == recProps);
         
         //Body should be copied in the received but not the sent
         assertTrue(usent_4.getPayload() == usent_1.getPayload());
         assertFalse(urec_4.getPayload() == urec_1.getPayload());
                       
      }
      finally
      {      
         if (conn != null)
         {
            conn.close();
         }
      }
      
   }
   
   
   
   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------
   
}


