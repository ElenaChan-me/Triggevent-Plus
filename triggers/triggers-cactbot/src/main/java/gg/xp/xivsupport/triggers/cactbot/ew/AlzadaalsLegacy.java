package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/alzadaals_legacy.ts をもとに移植
@CalloutRepo(name = "Alzadaals Legacy", duty = KnownDuty.AlzadaalsLegacy)
public class AlzadaalsLegacy extends AutoChildEventHandler implements FilteredEventHandler {

	// 触手の着地点は flags で決まるが、同じ flags でも青と赤で着地点が違う
	private static final Map<Long, String> CYAN_TENTACLE_LOCATIONS = Map.of(
			0x00080004L, "S",
			0x00200004L, "W",
			0x00800004L, "N",
			0x02000004L, "E");
	private static final Map<Long, String> SCARLET_TENTACLE_LOCATIONS = Map.of(
			0x00080004L, "NW",
			0x00200004L, "NE",
			0x00800004L, "SE",
			0x02000004L, "SW");
	private static final Map<String, String> SCARLET_ONLY_SAFE = Map.of(
			"NW", "SE",
			"NE", "SW",
			"SE", "NW",
			"SW", "NE");
	// 青の位置 → 赤の位置 → 安置。青の隣の方角から、赤の反対側の空いている斜めまでが安全
	private static final Map<String, Map<String, String[]>> BOTH_TENTACLES_SAFE = Map.of(
			"N", Map.of("SE", new String[]{"W", "SW"}, "SW", new String[]{"E", "SE"}),
			"E", Map.of("NW", new String[]{"S", "SW"}, "SW", new String[]{"N", "NW"}),
			"S", Map.of("NW", new String[]{"E", "NE"}, "NE", new String[]{"W", "NW"}),
			"W", Map.of("NE", new String[]{"S", "SE"}, "SE", new String[]{"N", "NE"}));
	private static final long CYAN_TENTACLE_PATH = 0x10;
	private static final long SCARLET_TENTACLE_PATH = 0x11;
	private static final int TENTACLE_DELAY_MS = 2_300;
	private static final Duration GRAVITON_CANNON_LEAD = Duration.ofSeconds(4);

	private XivState state;

	public AlzadaalsLegacy(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlzadaalsLegacy);
	}

	private final List<MapEffectEvent> tentacleEffects = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		tentacleEffects.clear();
	}

	// アンブジャム

	@NpcCastCallout(0x6F60)
	private final ModifiableCallout<AbilityCastStart> bigWave = ModifiableCallout.durationBasedCall("Big Wave", "Raidwide with Bleed");

	@HandleEvents
	public void tentacleCollect(EventContext context, MapEffectEvent event) {
		if (CYAN_TENTACLE_LOCATIONS.containsKey(event.getFlags())) {
			tentacleEffects.add(event);
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> tentacleDigSingle = new ModifiableCallout<>("Tentacle Dig (Single)", "Go {dir1}");
	private final ModifiableCallout<AbilityUsedEvent> tentacleDigMultiple = new ModifiableCallout<>("Tentacle Dig (Multiple)", "Go {dir1} / {dir2}");
	private final ModifiableCallout<AbilityUsedEvent> tentacleDigUnknown = new ModifiableCallout<>("Tentacle Dig (Unknown)", "Avoid Tentacle Explosions");

	// 初回は赤い触手1本だけ
	@AutoFeed
	private final SequentialTrigger<BaseEvent> tentacleDigSingleSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x6F55) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(TENTACLE_DELAY_MS);
				String safe = null;
				if (tentacleEffects.size() == 1) {
					String loc = SCARLET_TENTACLE_LOCATIONS.get(tentacleEffects.get(0).getFlags());
					safe = loc == null ? null : SCARLET_ONLY_SAFE.get(loc);
				}
				tentacleEffects.clear();
				if (safe == null) {
					s.updateCall(tentacleDigUnknown, e1);
					return;
				}
				s.setParam("dir1", safe);
				s.updateCall(tentacleDigSingle, e1);
			});

	// 2回目以降は青と赤の2本
	@AutoFeed
	private final SequentialTrigger<BaseEvent> tentacleDigMultipleSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x6F59) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(TENTACLE_DELAY_MS);
				String[] safe = null;
				if (tentacleEffects.size() == 2) {
					String cyan = null;
					String scarlet = null;
					for (MapEffectEvent tentacle : tentacleEffects) {
						if (tentacle.getLocation() == CYAN_TENTACLE_PATH) {
							cyan = CYAN_TENTACLE_LOCATIONS.get(tentacle.getFlags());
						}
						else if (tentacle.getLocation() == SCARLET_TENTACLE_PATH) {
							scarlet = SCARLET_TENTACLE_LOCATIONS.get(tentacle.getFlags());
						}
					}
					if (cyan != null && scarlet != null) {
						safe = BOTH_TENTACLES_SAFE.get(cyan).get(scarlet);
					}
				}
				tentacleEffects.clear();
				if (safe == null) {
					s.updateCall(tentacleDigUnknown, e1);
					return;
				}
				s.setParam("dir1", safe[0]);
				s.setParam("dir2", safe[1]);
				s.updateCall(tentacleDigMultiple, e1);
			});

	@NpcCastCallout(0x731A)
	private final ModifiableCallout<AbilityCastStart> fountain = ModifiableCallout.durationBasedCall("Fountain", "Dodge 3 to 1");

	// アーマード・チャリオット

	@NpcCastCallout(0x6F1E)
	private final ModifiableCallout<AbilityCastStart> diffusionRay = ModifiableCallout.durationBasedCall("Diffusion Ray", "Raidwide");

	@NpcCastCallout(0x6F1F)
	private final ModifiableCallout<AbilityCastStart> railCannon = ModifiableCallout.durationBasedCall("Rail Cannon", "Buster on {event.target}");

	@NpcCastCallout(value = 0x6F19, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> articulatedBits = ModifiableCallout.durationBasedCall("Articulated Bits", "Avoid Bit Lasers");

	private final ModifiableCallout<AbilityCastStart> gravitonCannon = ModifiableCallout.durationBasedCall("Graviton Cannon", "Spread");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> gravitonCannonSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7373) && acs.getTarget().isThePlayer(),
			gravitonCannon, GRAVITON_CANNON_LEAD);

	// カピクル

	@NpcCastCallout(0x6F70)
	private final ModifiableCallout<AbilityCastStart> billowingBolts = ModifiableCallout.durationBasedCall("Billowing Bolts", "Raidwide");

	@NpcCastCallout(0x6F63)
	private final ModifiableCallout<AbilityCastStart> spinOut = ModifiableCallout.durationBasedCall("Spin Out", "Steer Away from Spikes");

	@NpcCastCallout(0x6F6A)
	private final ModifiableCallout<AbilityCastStart> powerSerge = ModifiableCallout.durationBasedCall("Power Serge", "Avoid Tethered Color");

	private final ModifiableCallout<HeadMarkerEvent> magnitudeOpus = new ModifiableCallout<>("Magnitude Opus", "Stack on {event.target}");

	@HandleEvents
	public void magnitudeOpus(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xA1)) {
			context.accept(magnitudeOpus.getModified(event));
		}
	}

	@PlayerHeadmarker(0x60)
	private final ModifiableCallout<HeadMarkerEvent> rotaryGale = new ModifiableCallout<>("Rotary Gale", "Spread");

	@NpcCastCallout(0x6F72)
	private final ModifiableCallout<AbilityCastStart> crewelSlice = ModifiableCallout.durationBasedCall("Crewel Slice", "Buster on {event.target}");

}
