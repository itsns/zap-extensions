/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2016 The ZAP development team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.ascanrules;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mockito;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.core.scanner.AbstractPlugin;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.HostProcess;
import org.parosproxy.paros.core.scanner.PluginFactory;
import org.parosproxy.paros.core.scanner.Scanner;
import org.parosproxy.paros.core.scanner.ScannerParam;
import org.parosproxy.paros.extension.ExtensionLoader;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.network.ConnectionParam;
import org.parosproxy.paros.network.HttpMalformedHeaderException;
import org.parosproxy.paros.network.HttpMessage;
import org.testng.reporters.Files;
import org.zaproxy.zap.extension.ScannerTestUtils;
import org.zaproxy.zap.extension.ascan.ScanPolicy;
//import org.zaproxy.zap.extension.ruleconfig.RuleConfigParam;
import org.zaproxy.zap.utils.ClassLoaderUtil;

public abstract class ActiveScannerTest extends ScannerTestUtils {

    private static final String INSTALL_PATH = "test/resources/install";
    private static final File HOME_DIR = new File("test/resources/home");
    private static final String BASE_RESOURCE_DIR = "test/resources/org/zaproxy/zap/extension/ascanrules/";

    protected AbstractPlugin rule;
    protected HostProcess parent;
    protected List<Alert> alertsRaised;
    protected HTTPDTestServer nano;

    @BeforeClass
    public static void beforeClass() {
    }

    public ActiveScannerTest() {
        super();
    }

    @Before
    public void setUp() throws Exception {
/*
        // Useful if you need to get some info when debugging
        BasicConfigurator.configure();
        ConsoleAppender ca = new ConsoleAppender();
        ca.setWriter(new OutputStreamWriter(System.out));
        ca.setLayout(new PatternLayout("%-5p [%t]: %m%n"));
        Logger.getRootLogger().addAppender(ca);
        Logger.getRootLogger().setLevel(Level.DEBUG);
*/
        Constant.setZapInstall(INSTALL_PATH);
        HOME_DIR.mkdirs();
        Constant.setZapHome(HOME_DIR.getAbsolutePath());

        File langDir = new File(Constant.getZapInstall(), "lang");
        ClassLoaderUtil.addFile(langDir.getAbsolutePath());
        
        ExtensionLoader extLoader = Mockito.mock(ExtensionLoader.class);
        Control control = Mockito.mock(Control.class);
        Mockito.when (control.getExtensionLoader()).thenReturn(extLoader);

        // Init all the things
        Constant.getInstance();
        mockMessages(new ExtensionAscanRules());
        Control.initSingletonForTesting();
        Model.getSingleton();

        PluginFactory pluginFactory = Mockito.mock(PluginFactory.class);
        ScanPolicy scanPolicy = Mockito.mock(ScanPolicy.class);
        Mockito.when(scanPolicy.getPluginFactory()).thenReturn(pluginFactory);
        
        ConnectionParam connectionParam = new ConnectionParam();
        
        ScannerParam scannerParam = new ScannerParam();
        // Will need this once we go to the release after 2.5.0
        // RuleConfigParam ruleConfigParam = new RuleConfigParam();
        Scanner parentScanner =
                new Scanner(scannerParam, connectionParam, scanPolicy);
        //, ruleConfigParam);

        int port = 9090;
        nano = new HTTPDTestServer(port);
        nano.start();
        
        alertsRaised = new ArrayList<>();
        parent = new HostProcess(
                "localhost:" + port,
                parentScanner, 
                scannerParam, 
                connectionParam, 
                scanPolicy) {
                //ruleConfigParam) {
            @Override
            public void alertFound(Alert arg1) {
                alertsRaised.add(arg1);
            }
        };
        
        rule = createScanner();
    }
    
    @After
    public void shutDown() throws Exception {
        nano.stop();
        File dir = new File("test/resources/home");
        FileUtils.deleteDirectory(dir);
    }

    protected abstract AbstractPlugin createScanner();
    
    protected HttpMessage getHttpMessage(String url) throws HttpMalformedHeaderException {
        return this.getHttpMessage("GET", url, "<html></html>");
        
    }
    protected HttpMessage getHttpMessage(String method, String url, String body) throws HttpMalformedHeaderException {
        HttpMessage msg = new HttpMessage();
        StringBuilder reqHeaderSB = new StringBuilder();
        reqHeaderSB.append(method);
        reqHeaderSB.append(" http://localhost:");
        reqHeaderSB.append(this.nano.getListeningPort()); 
        reqHeaderSB.append(url);
        reqHeaderSB.append(" HTTP/1.1\r\n");
        reqHeaderSB.append("Host: www.any_domain_name.org\r\n");
        reqHeaderSB.append("User-Agent: ZAP\r\n");
        reqHeaderSB.append("Pragma: no-cache\r\n");
        msg.setRequestHeader(reqHeaderSB.toString());

        msg.setResponseBody(body);
        
        StringBuilder respHeaderSB = new StringBuilder();
        respHeaderSB.append("HTTP/1.1 200 OK\r\n");
        respHeaderSB.append("Server: Apache-Coyote/1.1\r\n");
        respHeaderSB.append("Content-Type: text/html;charset=ISO-8859-1\r\n");
        respHeaderSB.append("Content-Length: ");
        respHeaderSB.append(msg.getResponseBody().length());
        respHeaderSB.append("\r\n");
        msg.setResponseHeader(respHeaderSB.toString());

        return msg;
    }
    
    public String getHtml(String name) {
        return this.getHtml(name, (Map<String, String>)null);
    }

    public String getHtml(String name, String[][] params) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i=0; i < params.length; i++) {
            map.put(params[i][0], params[i][1]);
        }
        return this.getHtml(name, map);
    }

    public String getHtml(String name, Map<String, String> params) {
        String fileName = BASE_RESOURCE_DIR + this.getClass().getSimpleName() + "/" + name;
        try (FileInputStream fis = new FileInputStream(fileName)) {
            String html = Files.readFile(fis);
            if (params != null) {
                // Replace all of the supplied parameters
                for (Entry<String, String> entry : params.entrySet()) {
                    html = html.replaceAll("@@@" + entry.getKey() + "@@@", entry.getValue());
                }
            }
            return html;
        } catch (IOException e) {
            System.err.println("Failed to read file " + new File(fileName).getAbsolutePath());
            throw new RuntimeException(e);
        }
    }
}