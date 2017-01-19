/*
 * Copyright 2017 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.sockjs;

import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class StreamingTransport extends TransportBasedListener {
    private static final Logger logger = LoggerFactory.getLogger(StreamingTransport.class);

    private static final byte[] padding;
    private static final String paddingText;

    static {
        StringBuilder whitespace = new StringBuilder();
        for (int i = 0; i < 2048; i++) {
            whitespace.append("h");
        }
        whitespace.append("\n");
        paddingText = whitespace.toString();
        padding = paddingText.getBytes();
    }

    @Override
    public void onPreSuspend(AtmosphereResourceEvent event) {
        AtmosphereResponse response = event.getResource().getResponse();
        response.setContentType("application/javascript");
        try {

            response.write(padding, true).flushBuffer();
            response.write("o\n".getBytes(), true).flushBuffer();
        } catch (IOException e) {
            logger.trace("", e);
        }
    }

}
