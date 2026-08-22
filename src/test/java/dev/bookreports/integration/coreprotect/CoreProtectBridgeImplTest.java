package dev.bookreports.integration.coreprotect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import net.coreprotect.CoreProtectAPI;
import org.junit.jupiter.api.Test;

class CoreProtectBridgeImplTest {

    private final CoreProtectAPI api = mock(CoreProtectAPI.class);
    private final CoreProtectBridge bridge = new CoreProtectBridgeImpl(api, 300, 20);

    @Test
    void returnsNullWhenCoreProtectHasNothingOnFile() {
        when(api.performLookup(300, List.of("Steve"), null, null, null, null, 0, null)).thenReturn(List.of());

        assertNull(bridge.recentActivity(UUID.randomUUID(), "Steve"));
    }

    @Test
    void returnsNullWhenPerformLookupReturnsNull() {
        when(api.performLookup(300, List.of("Steve"), null, null, null, null, 0, null)).thenReturn(null);

        assertNull(bridge.recentActivity(UUID.randomUUID(), "Steve"));
    }

    @Test
    void groupsAndCountsActionsWithTheLookbackWindowInTheSummary() {
        String[] breakRowA = {"row-break-a"};
        String[] breakRowB = {"row-break-b"};
        String[] placeRow = {"row-place"};
        when(api.performLookup(300, List.of("Steve"), null, null, null, null, 0, null))
                .thenReturn(List.of(breakRowA, breakRowB, placeRow));
        stubAction(breakRowA, "break");
        stubAction(breakRowB, "break");
        stubAction(placeRow, "place");

        String summary = bridge.recentActivity(UUID.randomUUID(), "Steve");

        assertEquals("2x break, 1x place (last 5m)", summary);
    }

    @Test
    void formatsASubMinuteLookbackInSeconds() {
        CoreProtectBridge fortySecondBridge = new CoreProtectBridgeImpl(api, 45, 20);
        String[] row = {"row"};
        when(api.performLookup(45, List.of("Steve"), null, null, null, null, 0, null))
                .thenReturn(List.<String[]>of(row));
        stubAction(row, "break");

        assertEquals("1x break (last 45s)", fortySecondBridge.recentActivity(UUID.randomUUID(), "Steve"));
    }

    @Test
    void stopsScanningAfterMaxEntriesRawRows() {
        CoreProtectBridge cappedBridge = new CoreProtectBridgeImpl(api, 300, 1);
        String[] first = {"row-1"};
        String[] second = {"row-2"};
        when(api.performLookup(300, List.of("Steve"), null, null, null, null, 0, null))
                .thenReturn(List.of(first, second));
        stubAction(first, "break");
        stubAction(second, "place");

        assertEquals("1x break (last 5m)", cappedBridge.recentActivity(UUID.randomUUID(), "Steve"));
    }

    private void stubAction(String[] row, String action) {
        CoreProtectAPI.ParseResult parseResult = mock(CoreProtectAPI.ParseResult.class);
        when(parseResult.getActionString()).thenReturn(action);
        when(api.parseResult(row)).thenReturn(parseResult);
    }
}
