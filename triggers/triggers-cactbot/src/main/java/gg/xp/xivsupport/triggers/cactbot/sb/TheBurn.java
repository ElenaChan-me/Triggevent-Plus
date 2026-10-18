package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/the_burn.ts をもとに移植
@CalloutRepo(name = "The Burn", duty = KnownDuty.TheBurn)
public class TheBurn extends AutoChildEventHandler implements FilteredEventHandler {

	// ヘデテットとディフェクティブ・ドローンで同じマーカーを使う
	private static final long sharedMarkerId = 0x02;
	private static final long miningDroneNameId = 7670;

	private XivState state;

	public TheBurn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheBurn);
	}

	// 2ボス戦に入ったかどうか。ワイプしても戻らないので PullStartedEvent ではリセットしない
	private boolean hedetetDone;

	// ヘデテット

	@NpcCastCallout(0x3193)
	private final ModifiableCallout<AbilityCastStart> crystalNeedle = ModifiableCallout.durationBasedCall("Crystal Needle", "Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> hailfire = new ModifiableCallout<>("Hailfire", "Hide Behind Crystal");
	private final ModifiableCallout<HeadMarkerEvent> aetherochemicalResidue = new ModifiableCallout<>("Aetherochemical Residue", "Puddle on You");
	private final RepeatSuppressor hailfireSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void sharedMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(sharedMarkerId)) {
			return;
		}
		if (hedetetDone) {
			if (event.getTarget().isThePlayer()) {
				context.accept(aetherochemicalResidue.getModified(event));
			}
		}
		else if (hailfireSupp.check(event)) {
			context.accept(hailfire.getModified(event));
		}
	}

	@PlayerHeadmarker(0x60)
	private final ModifiableCallout<HeadMarkerEvent> shardstrike = new ModifiableCallout<>("Shardstrike", "Spread, Avoid Crystals");

	@NpcCastCallout(0x3191)
	private final ModifiableCallout<AbilityCastStart> crystalShardfall = ModifiableCallout.durationBasedCall("Crystal Shardfall", "Hide Behind Crystal");

	// ドーナツの内側の輪に入らないと当たる
	@NpcCastCallout(0x3192)
	private final ModifiableCallout<AbilityCastStart> crystalDissonance = ModifiableCallout.durationBasedCall("Crystal Dissonance", "Get Under");

	// ディフェクティブ・ドローン

	private final ModifiableCallout<AbilityCastStart> aetherochemicalFlame = ModifiableCallout.durationBasedCall("Aetherochemical Flame", "Raidwide");
	private final ModifiableCallout<AbilityCastStart> aetherochemicalCoil = ModifiableCallout.durationBasedCall("Aetherochemical Coil", "Buster on {event.target}");

	@HandleEvents
	public void droneCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2D73)) {
			hedetetDone = true;
			context.accept(aetherochemicalFlame.getModified(event));
		}
		else if (event.abilityIdMatches(0x2D72)) {
			hedetetDone = true;
			context.accept(aetherochemicalCoil.getModified(event));
		}
	}

	private final ModifiableCallout<RawAddCombatantEvent> throttle = new ModifiableCallout<>("Throttle", "In Line with Smoking Drone");
	private final RepeatSuppressor throttleSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		if (nameId == miningDroneNameId && throttleSupp.check(event)) {
			context.accept(throttle.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x2D78, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> aditDriver = ModifiableCallout.durationBasedCall("Adit Driver", "Avoid Rock Biters");

	// ミストドラゴン

	@NpcCastCallout(0x314B)
	private final ModifiableCallout<AbilityCastStart> rimeWreath = ModifiableCallout.durationBasedCall("Rime Wreath", "Raidwide");

	// チリングアスピレーションも同じマーカーで扱う
	private final ModifiableCallout<HeadMarkerEvent> frostBreathOnYou = new ModifiableCallout<>("Frost Breath (On You)", "Puddle and Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> frostBreathOther = new ModifiableCallout<>("Frost Breath (Other)", "Avoid Marker Cone");

	@HandleEvents
	public void frostBreath(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x1A, 0x0E)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(frostBreathOnYou.getModified(event));
		}
		else {
			context.accept(frostBreathOther.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x3144, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> fogPlume = ModifiableCallout.durationBasedCall("Fog Plume", "Bait Star Explosions");

}
