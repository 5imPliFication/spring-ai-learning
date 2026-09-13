package com.example.ai.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.StdioTransportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class McpCalendarIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    StdioTransportAutoConfiguration.class,
                    McpClientAutoConfiguration.class,
                    McpToolCallbackAutoConfiguration.class))
            .withPropertyValues(
                    "spring.ai.mcp.client.enabled=true",
                    "spring.ai.mcp.client.stdio.connections.google-calendar.command=/home/simpi/go/bin/google-mcp-server",
                    "spring.ai.mcp.client.stdio.connections.google-calendar.args=",
                    "spring.ai.mcp.client.stdio.connections.google-calendar.env.DUMMY=1");

    @Test
    void mcpAutoconfigDiscoversGoogleCalendarTools() {
        assumeTrue(java.nio.file.Files.exists(java.nio.file.Path.of("/home/simpi/go/bin/google-mcp-server")),
                "google-mcp-server binary not installed; skipping");

        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SyncMcpToolCallbackProvider.class);

            SyncMcpToolCallbackProvider provider = context.getBean(SyncMcpToolCallbackProvider.class);
            ToolCallback[] tools = provider.getToolCallbacks();

            System.out.println("====== MCP TOOLS DISCOVERED (" + tools.length + ") ======");
            for (ToolCallback tool : tools) {
                System.out.println("  " + tool.getToolDefinition().name());
            }

            assertThat(tools).extracting(t -> t.getToolDefinition().name())
                    .contains("calendar_events_list", "calendar_event_create", "calendar_list");
        });
    }

    @Test
    void noConnectionsConfiguredYieldsNoTools() {
        ApplicationContextRunner noMcp = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        StdioTransportAutoConfiguration.class,
                        McpClientAutoConfiguration.class,
                        McpToolCallbackAutoConfiguration.class))
                .withPropertyValues("spring.ai.mcp.client.enabled=true");

        noMcp.run(context -> {
            assertThat(context).hasSingleBean(SyncMcpToolCallbackProvider.class);
            SyncMcpToolCallbackProvider provider = context.getBean(SyncMcpToolCallbackProvider.class);
            assertThat(provider.getToolCallbacks()).isEmpty();
        });
    }
}