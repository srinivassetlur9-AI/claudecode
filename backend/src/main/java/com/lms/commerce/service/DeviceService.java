package com.lms.commerce.service;

import com.lms.commerce.domain.Device;
import com.lms.commerce.domain.DeviceEvent;
import com.lms.commerce.repo.DeviceEventRepository;
import com.lms.commerce.repo.DeviceRepository;
import com.lms.commerce.repo.DeviceUserLock;
import com.lms.commerce.web.CommerceDtos.*;
import com.lms.identity.config.LmsProperties;
import com.lms.identity.domain.Role;
import com.lms.identity.domain.User;
import com.lms.identity.security.Hashing;
import com.lms.identity.service.DeviceGate;
import com.lms.identity.service.SessionService.ClientInfo;
import com.lms.identity.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedicated-device control for learners: at most {@code lms.commerce.max-devices} (default 2) active devices.
 * Registration happens at login (the {@link DeviceGate} contract), a third device is refused, and every change is
 * written to an append-only audit trail. The raw device id is never stored, only its SHA-256.
 */
@Service
public class DeviceService implements DeviceGate {
    static final String REGISTERED = "REGISTERED", LOGIN = "LOGIN", BLOCKED = "BLOCKED", DEACTIVATED = "DEACTIVATED",
            REPLACED = "REPLACED", REACTIVATED = "REACTIVATED", ADMIN_RESET = "ADMIN_RESET";

    private final DeviceRepository devices;
    private final DeviceEventRepository events;
    private final DeviceUserLock userLock;
    private final SubscriptionService subscriptions;
    private final EntitlementService entitlements;
    private final int max;

    public DeviceService(DeviceRepository devices, DeviceEventRepository events, DeviceUserLock userLock,
                         SubscriptionService subscriptions, EntitlementService entitlements, LmsProperties props) {
        this.devices = devices;
        this.events = events;
        this.userLock = userLock;
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.max = props.commerce().maxDevices();
    }

    // ---- DeviceGate (called by Module 1 during login / refresh) ----

    /** noRollbackFor: the BLOCKED audit row must survive the exception that refuses the login. */
    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public Long admit(User user, ClientInfo client) {
        if (!user.roles().contains(Role.LEARNER)) return null; // limits apply to learner accounts only
        String key = client.deviceKey();
        if (key == null || key.isBlank() || key.length() < 8 || key.length() > 200) {
            throw ApiException.badRequest("DEVICE_ID_REQUIRED", "This app must identify the device it runs on (X-Device-Id)");
        }
        userLock.lockById(user.getId()); // serialise concurrent logins of the same account
        Instant now = Instant.now();
        String hash = Hashing.sha256(key);
        String name = clean(client.deviceName(), 150, "Unknown device");
        String platform = clean(client.userAgent(), 100, null);
        long active = devices.countByUserIdAndStatus(user.getId(), Device.ACTIVE);

        Device d = devices.findByUserIdAndDeviceKeyHash(user.getId(), hash).orElse(null);
        if (d != null && d.isActive()) {
            d.setLastSeenAt(now);
            d.setLastIp(client.ip());
            record(user.getId(), d.getId(), LOGIN, null, client.ip());
            return d.getId();
        }
        if (active >= max) {
            record(user.getId(), d == null ? null : d.getId(), BLOCKED, "Login refused from \"" + name + "\": " + max + " devices already registered", client.ip());
            throw ApiException.forbidden("DEVICE_LIMIT_REACHED", "This account is already registered on " + max
                    + " devices. Sign in on one of them and deactivate a device (My Devices), or contact support.");
        }
        if (d != null) { // a previously deactivated device comes back into a free slot
            d.setStatus(Device.ACTIVE);
            d.setDeactivatedAt(null);
            d.setDeactivationReason(null);
            d.setLastSeenAt(now);
            d.setLastIp(client.ip());
            record(user.getId(), d.getId(), REACTIVATED, null, client.ip());
            return d.getId();
        }
        d = new Device();
        d.setUserId(user.getId());
        d.setDeviceKeyHash(hash);
        d.setName(name);
        d.setPlatform(platform);
        d.setRegisteredAt(now);
        d.setLastSeenAt(now);
        d.setLastIp(client.ip());
        devices.saveAndFlush(d);
        // If the learner freed a slot with "replace", link the oldest such device to this one.
        Device finalD = d;
        devices.findByUserIdAndStatusAndReplacedByIsNullOrderByDeactivatedAtAsc(user.getId(), Device.REPLACED)
                .stream().findFirst().ifPresent(old -> old.setReplacedBy(finalD.getId()));
        record(user.getId(), d.getId(), REGISTERED, name, client.ip());
        return d.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(Long deviceId) {
        return devices.findById(deviceId).map(Device::isActive).orElse(false);
    }

    // ---- learner API ----

    @Transactional(readOnly = true)
    public DevicesView list(Long userId, String currentKey) {
        String currentHash = currentKey == null || currentKey.isBlank() ? null : Hashing.sha256(currentKey);
        List<DeviceView> rows = devices.findByUserIdOrderByRegisteredAtDesc(userId).stream()
                .map(d -> view(d, d.getDeviceKeyHash().equals(currentHash))).toList();
        return new DevicesView(max, devices.countByUserIdAndStatus(userId, Device.ACTIVE), rows);
    }

    @Transactional(readOnly = true)
    public List<DeviceEventView> history(Long userId) {
        return events.findTop200ByUserIdOrderByIdDesc(userId).stream().map(DeviceService::view).toList();
    }

    @Transactional
    public DeviceView deactivate(Long userId, Long deviceId, String reason, String ip) {
        return release(userId, deviceId, Device.DEACTIVATED, DEACTIVATED, reason == null ? "Deactivated by the learner" : reason, ip);
    }

    /** Same as deactivate, but recorded as a replacement so the audit trail shows the slot was freed for a new device. */
    @Transactional
    public DeviceView replace(Long userId, Long deviceId, String reason, String ip) {
        return release(userId, deviceId, Device.REPLACED, REPLACED, reason == null ? "Replaced by another device" : reason, ip);
    }

    /** Device/licence validation API: may this device use the learner's content right now? */
    @Transactional
    public LicenseView validate(Long userId, Set<Role> roles, String key) {
        String planCode = subscriptions.current(userId).planCode();
        int ents = entitlements.activeCount(userId);
        long active = devices.countByUserIdAndStatus(userId, Device.ACTIVE);
        if (!roles.contains(Role.LEARNER)) return new LicenseView(true, "NOT_APPLICABLE", null, active, max, planCode, ents);
        if (key == null || key.isBlank()) return new LicenseView(false, "DEVICE_ID_REQUIRED", null, active, max, planCode, ents);
        Device d = devices.findByUserIdAndDeviceKeyHash(userId, Hashing.sha256(key)).orElse(null);
        if (d == null) return new LicenseView(false, "DEVICE_NOT_REGISTERED", null, active, max, planCode, ents);
        if (!d.isActive()) return new LicenseView(false, "DEVICE_" + d.getStatus(), d.getId(), active, max, planCode, ents);
        d.setLastSeenAt(Instant.now());
        return new LicenseView(true, "OK", d.getId(), active, max, planCode, ents);
    }

    // ---- admin API ----

    @Transactional(readOnly = true)
    public DevicesView adminList(Long userId) {
        return list(userId, null);
    }

    @Transactional(readOnly = true)
    public List<DeviceEventView> adminEvents(Long userId) {
        return (userId == null ? events.findTop300ByOrderByIdDesc() : events.findTop200ByUserIdOrderByIdDesc(userId))
                .stream().map(DeviceService::view).toList();
    }

    /** Support action for a learner who lost their devices and can no longer sign in anywhere. */
    @Transactional
    public int adminReset(Long adminId, Long userId) {
        userLock.lockById(userId);
        List<Device> active = devices.findByUserIdAndStatus(userId, Device.ACTIVE);
        Instant now = Instant.now();
        for (Device d : active) {
            d.setStatus(Device.DEACTIVATED);
            d.setDeactivatedAt(now);
            d.setDeactivationReason("Reset by support");
            devices.revokeSessions(d.getId(), now, "DEVICE_DEACTIVATED");
            record(userId, d.getId(), ADMIN_RESET, "Reset by user #" + adminId, null);
        }
        return active.size();
    }

    // ---- internals ----

    private DeviceView release(Long userId, Long deviceId, String status, String eventType, String reason, String ip) {
        userLock.lockById(userId);
        Device d = devices.findByIdAndUserId(deviceId, userId).orElseThrow(() -> ApiException.notFound("Device not found"));
        if (!d.isActive()) throw ApiException.conflict("DEVICE_NOT_ACTIVE", "This device is already deactivated");
        Instant now = Instant.now();
        d.setStatus(status);
        d.setDeactivatedAt(now);
        d.setDeactivationReason(clean(reason, 200, null));
        devices.revokeSessions(d.getId(), now, "DEVICE_DEACTIVATED"); // its tokens stop working immediately
        record(userId, d.getId(), eventType, reason, ip);
        return view(d, false);
    }

    private void record(Long userId, Long deviceId, String type, String detail, String ip) {
        DeviceEvent e = new DeviceEvent();
        e.setUserId(userId);
        e.setDeviceId(deviceId);
        e.setEventType(type);
        e.setDetail(clean(detail, 300, null));
        e.setIpAddress(clean(ip, 45, null));
        events.save(e);
    }

    private static String clean(String v, int max, String fallback) {
        if (v == null || v.isBlank()) return fallback;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static DeviceView view(Device d, boolean current) {
        return new DeviceView(d.getId(), d.getName(), d.getPlatform(), d.getStatus(), d.getRegisteredAt(), d.getLastSeenAt(),
                d.getLastIp(), d.getDeactivatedAt(), d.getDeactivationReason(), current);
    }

    private static DeviceEventView view(DeviceEvent e) {
        return new DeviceEventView(e.getId(), e.getUserId(), e.getDeviceId(), e.getEventType(), e.getDetail(), e.getIpAddress(), e.getCreatedAt());
    }
}
