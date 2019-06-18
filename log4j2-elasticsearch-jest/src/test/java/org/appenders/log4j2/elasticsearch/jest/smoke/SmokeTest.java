package org.appenders.log4j2.elasticsearch.jest.smoke;

/*-
 * #%L
 * log4j2-elasticsearch
 * %%
 * Copyright (C) 2018 Rafal Foltynski
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import io.searchbox.client.config.HttpClientConfig;
import org.appenders.log4j2.elasticsearch.AsyncBatchDelivery;
import org.appenders.log4j2.elasticsearch.Auth;
import org.appenders.log4j2.elasticsearch.BatchDelivery;
import org.appenders.log4j2.elasticsearch.CertInfo;
import org.appenders.log4j2.elasticsearch.Credentials;
import org.appenders.log4j2.elasticsearch.ElasticsearchAppender;
import org.appenders.log4j2.elasticsearch.IndexNameFormatter;
import org.appenders.log4j2.elasticsearch.IndexTemplate;
import org.appenders.log4j2.elasticsearch.JacksonJsonLayout;
import org.appenders.log4j2.elasticsearch.PooledItemSourceFactory;
import org.appenders.log4j2.elasticsearch.RollingIndexNameFormatter;
import org.appenders.log4j2.elasticsearch.jest.BasicCredentials;
import org.appenders.log4j2.elasticsearch.jest.BufferedJestHttpObjectFactory;
import org.appenders.log4j2.elasticsearch.jest.JestHttpObjectFactory;
import org.appenders.log4j2.elasticsearch.jest.PEMCertInfo;
import org.appenders.log4j2.elasticsearch.jest.XPackAuth;
import org.appenders.log4j2.elasticsearch.smoke.SmokeTestBase;
import org.junit.BeforeClass;
import org.junit.Ignore;

@Ignore
public class SmokeTest extends SmokeTestBase {

    public static final int BATCH_SIZE = 5000;
    public static final int ADDITIONAL_BATCH_SIZE = (int) (BATCH_SIZE * 0.2); // prevent tiny batches by allowing notifier to
    public static final int INITIAL_ITEM_POOL_SIZE = 20000;
    public static final int INITIAL_ITEM_SIZE_IN_BYTES = 1024;
    public static final int INITIAL_BATCH_POOL_SIZE = 4;

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("log4j.configurationFile", "log4j2.xml");
    }

    @Override
    public ElasticsearchAppender.Builder createElasticsearchAppenderBuilder(boolean messageOnly, boolean buffered, boolean secured) {

        JestHttpObjectFactory.Builder jestHttpObjectFactoryBuilder;
        if (buffered) {
            jestHttpObjectFactoryBuilder = BufferedJestHttpObjectFactory.newBuilder();

            int estimatedBatchSizeInBytes = BATCH_SIZE * INITIAL_ITEM_SIZE_IN_BYTES;

            ((BufferedJestHttpObjectFactory.Builder)jestHttpObjectFactoryBuilder).withItemSourceFactory(
                    PooledItemSourceFactory.newBuilder()
                            .withPoolName("batchPool")
                            .withInitialPoolSize(INITIAL_BATCH_POOL_SIZE)
                            .withItemSizeInBytes(estimatedBatchSizeInBytes)
                            .withMonitored(true)
                            .withMonitorTaskInterval(10000)
                            .build()
            );
        } else {
            jestHttpObjectFactoryBuilder = JestHttpObjectFactory.newBuilder();
        }

        jestHttpObjectFactoryBuilder.withConnTimeout(1000)
                .withReadTimeout(10000)
                .withIoThreadCount(8)
                .withDefaultMaxTotalConnectionPerRoute(8)
                .withMaxTotalConnection(8);

        if (secured) {
            jestHttpObjectFactoryBuilder.withServerUris("https://localhost:9200")
                    .withAuth(getAuth());
        } else {
            jestHttpObjectFactoryBuilder.withServerUris("http://localhost:9200");
        }

        IndexTemplate indexTemplate = new IndexTemplate.Builder()
                .withName("log4j2_test_jest")
                .withPath("classpath:indexTemplate.json")
                .build();

        BatchDelivery asyncBatchDelivery = AsyncBatchDelivery.newBuilder()
                .withClientObjectFactory(jestHttpObjectFactoryBuilder.build())
                .withBatchSize(BATCH_SIZE + ADDITIONAL_BATCH_SIZE)
                .withDeliveryInterval(1000)
                .withIndexTemplate(indexTemplate)
                .build();

        IndexNameFormatter indexNameFormatter = RollingIndexNameFormatter.newBuilder()
                .withIndexName("log4j2_test_jest")
                .withPattern("yyyy-MM-dd-HH")
                .build();


        JacksonJsonLayout.Builder layoutBuilder = JacksonJsonLayout.newBuilder();
        if (buffered) {
            PooledItemSourceFactory sourceFactoryConfig = PooledItemSourceFactory.newBuilder()
                    .withPoolName("itemPool")
                    .withInitialPoolSize(INITIAL_ITEM_POOL_SIZE)
                    .withItemSizeInBytes(INITIAL_ITEM_SIZE_IN_BYTES)
                    .withMonitored(true)
                    .withMonitorTaskInterval(10000)
                    .build();
            layoutBuilder.withItemSourceFactory(sourceFactoryConfig).build();
        }

        return ElasticsearchAppender.newBuilder()
                .withName(DEFAULT_APPENDER_NAME)
                .withMessageOnly(messageOnly)
                .withBatchDelivery(asyncBatchDelivery)
                .withIndexNameFormatter(indexNameFormatter)
                .withLayout(layoutBuilder.build())
                .withIgnoreExceptions(false);
    }

    private static Auth<HttpClientConfig.Builder> getAuth() {
        CertInfo certInfo = PEMCertInfo.newBuilder()
                .withKeyPath(System.getProperty("pemCertInfo.keyPath"))
                .withKeyPassphrase(System.getProperty("pemCertInfo.keyPassphrase"))
                .withClientCertPath(System.getProperty("pemCertInfo.clientCertPath"))
                .withCaPath(System.getProperty("pemCertInfo.caPath"))
                .build();

//        CertInfo certInfo = JKSCertInfo.newBuilder()
//                .withKeystorePath(System.getProperty("jksCertInfo.keystorePath"))
//                .withKeystorePassword(System.getProperty("jksCertInfo.keystorePassword"))
//                .withTruststorePath(System.getProperty("jksCertInfo.truststorePath"))
//                .withTruststorePassword(System.getProperty("jksCertInfo.truststorePassword"))
//                .build();

        Credentials credentials = BasicCredentials.newBuilder()
                .withUsername("admin")
                .withPassword("changeme")
                .build();

        return XPackAuth.newBuilder()
                .withCertInfo(certInfo)
                .withCredentials(credentials)
                .build();
    }

}
