/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.microsoft.applicationinsights.serviceprofilerapi.upload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.microsoft.applicationinsights.serviceprofilerapi.client.ClientClosedException;
import com.microsoft.applicationinsights.serviceprofilerapi.client.ServiceProfilerClientV2;
import com.microsoft.applicationinsights.serviceprofilerapi.client.contract.ArtifactAcceptedResponse;
import com.microsoft.applicationinsights.serviceprofilerapi.client.contract.BlobAccessPass;
import com.microsoft.applicationinsights.profileUploader.ServiceProfilerIndex;
import com.microsoft.applicationinsights.serviceprofilerapi.client.uploader.UploadContext;
import com.microsoft.applicationinsights.serviceprofilerapi.client.uploader.UploadFinishArgs;
import io.reactivex.Single;
import org.junit.*;

public class ServiceProfilerUploaderTest {
    @Test
    public void uploadFileGoodPathReturnsExpectedResponse() throws IOException {

        ServiceProfilerClientV2 serviceProfilerClient = stubServiceProfilerClient();

        File tmpFile = createFakeJfrFile();
        UUID appId = UUID.randomUUID();

        ServiceProfilerUploader serviceProfilerUploader = new ServiceProfilerUploader(
                serviceProfilerClient,
                "a-machine-name",
                "a-process-id",
                appId::toString,
                "a-upload-url"
        ) {
            @Override
            protected Single<UploadFinishArgs> performUpload(UploadContext uploadContext, BlobAccessPass uploadPass, AsynchronousFileChannel fileChannel) {
                return Single.just(new UploadFinishArgs("a-stamp-id", "a-timestamp"));
            }

            @Override
            protected void uploadToCustomStore(UploadContext uploadContext, File zippedTraceFile) {
                //NOP
            }
        };

        serviceProfilerUploader
                .uploadJfrFile(
                        "a-trigger",
                        321,
                        tmpFile,
                        0.0,
                        0.0)
                .subscribe(
                        result -> {
                            Assert.assertEquals("a-stamp-id",
                                    result.getServiceProfilerIndex().getProperties().get(ServiceProfilerIndex.SERVICE_PROFILER_STAMPID_PROPERTY_NAME));

                            Assert.assertEquals("a-machine-name",
                                    result.getServiceProfilerIndex().getProperties().get(ServiceProfilerIndex.SERVICE_PROFILER_MACHINENAME_PROPERTY_NAME));

                            Assert.assertEquals("a-timestamp",
                                    result.getServiceProfilerIndex().getProperties().get(ServiceProfilerIndex.SERVICE_PROFILER_ETLFILESESSIONID_PROPERTY_NAME));

                            Assert.assertEquals(appId.toString(),
                                    result.getServiceProfilerIndex().getProperties().get(ServiceProfilerIndex.SERVICE_PROFILER_DATACUBE_PROPERTY_NAME));

                        });
    }


    @Test
    public void uploadWithoutAFileThrows() {

        ServiceProfilerClientV2 serviceProfilerClient = stubServiceProfilerClient();

        UUID appId = UUID.randomUUID();

        ServiceProfilerUploader serviceProfilerUploader = new ServiceProfilerUploader(serviceProfilerClient, "a-machine-name", "a-process-id", appId::toString);

        AtomicBoolean threw = new AtomicBoolean(false);
        serviceProfilerUploader
                .uploadJfrFile(
                        "a-trigger",
                        321,
                        new File("not-a-file"),
                        0.0,
                        0.0
                )
                .subscribe(result -> {
                }, e -> threw.set(true));

        Assert.assertTrue("Did not throw", threw.get());
    }

    private File createFakeJfrFile() throws IOException {
        File tmpFile = File.createTempFile("a-jfr-file", "jfr");
        FileOutputStream fos = new FileOutputStream(tmpFile);
        fos.write("foobar".getBytes());
        fos.close();
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    public static ServiceProfilerClientV2 stubServiceProfilerClient() {
        return new ServiceProfilerClientV2() {

            @Override public BlobAccessPass getUploadAccess(UUID profileId) {
                return new BlobAccessPass("https://localhost:99999/a-blob-uri", null, "a-sas-token");
            }

            @Override public ArtifactAcceptedResponse reportUploadFinish(UUID profileId, String etag) throws URISyntaxException, UnsupportedCharsetException, ClientClosedException {
                return null;
            }

            @Override public String getSettings(Date oldTimeStamp) {
                return null;
            }
        };
    }

}
