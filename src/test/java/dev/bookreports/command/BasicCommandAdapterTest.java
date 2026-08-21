package dev.bookreports.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.junit.jupiter.api.Test;

class BasicCommandAdapterTest {

    @Test
    void sendsAStartingUpMessageBeforeAnExecutorIsBound() {
        BasicCommandAdapter adapter = new BasicCommandAdapter("report", "bookreports.report");
        CommandSender sender = mock(CommandSender.class);
        CommandSourceStack stack = stackFor(sender);

        adapter.execute(stack, new String[0]);

        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void delegatesToTheBoundExecutorWithTheRegisteredLabel() {
        BasicCommandAdapter adapter = new BasicCommandAdapter("reportadmin", "bookreports.staff");
        CommandExecutor executor = mock(CommandExecutor.class);
        adapter.bind(executor);
        CommandSender sender = mock(CommandSender.class);
        CommandSourceStack stack = stackFor(sender);
        String[] args = {"list"};

        adapter.execute(stack, args);

        verify(executor).onCommand(eq(sender), isNull(), eq("reportadmin"), eq(args));
    }

    @Test
    void suggestReturnsEmptyBeforeAnExecutorIsBound() {
        BasicCommandAdapter adapter = new BasicCommandAdapter("report", "bookreports.report");

        assertTrue(adapter.suggest(stackFor(mock(CommandSender.class)), new String[0]).isEmpty());
    }

    @Test
    void suggestReturnsEmptyWhenTheBoundExecutorIsNotATabCompleter() {
        BasicCommandAdapter adapter = new BasicCommandAdapter("report", "bookreports.report");
        adapter.bind(mock(CommandExecutor.class));

        assertTrue(adapter.suggest(stackFor(mock(CommandSender.class)), new String[0]).isEmpty());
    }

    @Test
    void suggestDelegatesWhenTheBoundExecutorIsATabCompleter() {
        BasicCommandAdapter adapter = new BasicCommandAdapter("select", "bookreports.report");
        CompletingExecutor executor = new CompletingExecutor();
        adapter.bind(executor);
        CommandSender sender = mock(CommandSender.class);

        Collection<String> suggestions = adapter.suggest(stackFor(sender), new String[]{"a"});

        assertEquals(List.of(), suggestions);
        assertTrue(executor.suggestCalled);
    }

    @Test
    void permissionReturnsWhatTheConstructorWasGiven() {
        BasicCommandAdapter adapter = new BasicCommandAdapter("reportsreload", "bookreports.admin");

        assertEquals("bookreports.admin", adapter.permission());
    }

    @Test
    void unboundExecutorNeverReceivesTheStartingUpMessagePathTwice() {
        BasicCommandAdapter adapter = new BasicCommandAdapter("target", "bookreports.report");
        CommandExecutor executor = mock(CommandExecutor.class);
        CommandSender sender = mock(CommandSender.class);

        // Before bind: gets the fallback message, never reaches the (not-yet-bound) executor.
        adapter.execute(stackFor(sender), new String[0]);
        verify(executor, never()).onCommand(any(), any(), any(), any());

        // After bind: reaches the executor instead.
        adapter.bind(executor);
        adapter.execute(stackFor(sender), new String[0]);
        verify(executor).onCommand(eq(sender), isNull(), eq("target"), any());
    }

    private CommandSourceStack stackFor(CommandSender sender) {
        CommandSourceStack stack = mock(CommandSourceStack.class);
        when(stack.getSender()).thenReturn(sender);
        return stack;
    }

    /** A minimal executor that is also a tab completer, without Mockito's default-empty-list ambiguity. */
    private static final class CompletingExecutor implements CommandExecutor, TabCompleter {
        boolean suggestCalled;

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
            suggestCalled = true;
            return List.of();
        }
    }
}
