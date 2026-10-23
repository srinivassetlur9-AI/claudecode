package com.lms.commerce.web;

import com.lms.commerce.service.DeviceService;
import com.lms.commerce.web.CommerceDtos.*;
import com.lms.identity.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** A learner's own devices. Devices register automatically at login; these endpoints manage and verify them. */
@RestController
@RequestMapping("/api/v1/commerce/devices")
public class DeviceController {
    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @GetMapping
    public DevicesView mine(@AuthenticationPrincipal AuthPrincipal me, @RequestHeader(value = "X-Device-Id", required = false) String key) {
        return devices.list(me.userId(), key);
    }

    @GetMapping("/history")
    public List<DeviceEventView> history(@AuthenticationPrincipal AuthPrincipal me) {
        return devices.history(me.userId());
    }

    /** Device/licence validation: call before serving protected content, especially offline-capable apps. */
    @PostMapping("/validate")
    public LicenseView validate(@AuthenticationPrincipal AuthPrincipal me, @RequestHeader(value = "X-Device-Id", required = false) String key) {
        return devices.validate(me.userId(), me.roles(), key);
    }

    @PostMapping("/{id}/deactivate")
    public DeviceView deactivate(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id,
                                 @RequestBody(required = false) DeviceActionRequest req, HttpServletRequest http) {
        return devices.deactivate(me.userId(), id, req == null ? null : req.reason(), http.getRemoteAddr());
    }

    @PostMapping("/{id}/replace")
    public DeviceView replace(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id,
                              @RequestBody(required = false) DeviceActionRequest req, HttpServletRequest http) {
        return devices.replace(me.userId(), id, req == null ? null : req.reason(), http.getRemoteAddr());
    }
}
