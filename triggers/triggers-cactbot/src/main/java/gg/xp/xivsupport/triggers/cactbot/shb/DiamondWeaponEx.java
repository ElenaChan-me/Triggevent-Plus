package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/diamond_weapon-ex.ts をもとに移植
@CalloutRepo(name = "The Cloud Deck Extreme", duty = KnownDuty.TheCloudDeckExtreme)
public class DiamondWeaponEx extends AutoChildEventHandler implements FilteredEventHandler {

	// マーカーIDはインスタンスごとにずれるので、最初に付くフレア(0x57)からの相対値で見る
	private static final int FIRST_LC_OFFSET = 0x4F - 0x57;
	private static final int LAST_LC_OFFSET = 0x56 - 0x57;

	private XivState state;

	public DiamondWeaponEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheCloudDeckExtreme);
	}

	// フェーズ1・3

	@NpcCastCallout(0x5FA7)
	private final ModifiableCallout<AbilityCastStart> diamondRain = ModifiableCallout.durationBasedCall("Diamond Rain", "Raidwide");

	@NpcCastCallout(0x5F9B)
	private final ModifiableCallout<AbilityCastStart> purgeWestRain = ModifiableCallout.durationBasedCall("Adamant Purge West + Diamond Rain", "East, then Raidwide");

	@NpcCastCallout(0x5F9A)
	private final ModifiableCallout<AbilityCastStart> purgeEastRain = ModifiableCallout.durationBasedCall("Adamant Purge East + Diamond Rain", "West, then Raidwide");

	@NpcCastCallout(0x5FA5)
	private final ModifiableCallout<AbilityCastStart> purgeWestFlash = ModifiableCallout.durationBasedCall("Adamant Purge West + Diamond Flash", "East, then Stack");

	@NpcCastCallout(0x5FA4)
	private final ModifiableCallout<AbilityCastStart> purgeEastFlash = ModifiableCallout.durationBasedCall("Adamant Purge East + Diamond Flash", "West, then Stack");

	@NpcCastCallout(0x5FA3)
	private final ModifiableCallout<AbilityCastStart> purgeWestLaser = ModifiableCallout.durationBasedCall("Adamant Purge West + Homing Laser", "East, then Spread");

	@NpcCastCallout(0x5FA2)
	private final ModifiableCallout<AbilityCastStart> purgeEastLaser = ModifiableCallout.durationBasedCall("Adamant Purge East + Homing Laser", "West, then Spread");

	@NpcCastCallout(0x5FA8)
	private final ModifiableCallout<AbilityCastStart> photonBurst = ModifiableCallout.durationBasedCall("Photon Burst", "Flare");

	// フェーズ2

	@NpcCastCallout(0x5FBC)
	private final ModifiableCallout<AbilityCastStart> outrage = ModifiableCallout.durationBasedCall("Outrage", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> auriDoomsteadOnYou = ModifiableCallout.durationBasedCall("Auri Doomstead (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> auriDoomsteadSwap = ModifiableCallout.durationBasedCall("Auri Doomstead (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> auriDoomstead = ModifiableCallout.durationBasedCall("Auri Doomstead", "Buster on {event.target}");

	@HandleEvents
	public void auriDoomstead(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x5FBD)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(auriDoomsteadOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(auriDoomsteadSwap.getModified(event));
		}
		else {
			context.accept(auriDoomstead.getModified(event));
		}
	}

	@NpcCastCallout(0x5FAF)
	private final ModifiableCallout<AbilityCastStart> zigZag = ModifiableCallout.durationBasedCall("Zig-Zag", "Dodge Zig-Zag");

	@NpcCastCallout(0x5FB2)
	private final ModifiableCallout<AbilityCastStart> zigZagJump = ModifiableCallout.durationBasedCall("Zig-Zag Jump", "Dodge East/West");

	@NpcCastCallout(0x5FB5)
	private final ModifiableCallout<AbilityCastStart> northSouthJump = ModifiableCallout.durationBasedCall("North/South Jump", "Dodge Towards/Away");

	@NpcCastCallout(0x5FB7)
	private final ModifiableCallout<AbilityCastStart> verticalCleave = ModifiableCallout.durationBasedCall("Vertical Cleave", "Diagonal Knockback");

	@NpcCastCallout(0x5FC1)
	private final ModifiableCallout<AbilityCastStart> p2ArticulatedBits = ModifiableCallout.durationBasedCall("Articulated Bits (P2)", "Dodge Bits");

	private final ModifiableCallout<HeadMarkerEvent> limitCut = new ModifiableCallout<>("Limit Cut Number", "{num}", 25_000);

	@HandleEvents
	public void limitCut(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		int offset = event.getMarkerOffset();
		if (offset >= FIRST_LC_OFFSET && offset <= LAST_LC_OFFSET) {
			context.accept(limitCut.getModified(event, Map.<String, Object>of("num", offset - FIRST_LC_OFFSET + 1)));
		}
	}

	// フェーズ3

	@NpcCastCallout(0x5FA9)
	private final ModifiableCallout<AbilityCastStart> p3ArticulatedBits = ModifiableCallout.durationBasedCall("Articulated Bits (P3)", "Dodge Bits");

	@NpcCastCallout(0x5FAC)
	private final ModifiableCallout<AbilityCastStart> diamondShrapnel = ModifiableCallout.durationBasedCall("Diamond Shrapnel", "Bait Puddles");

	private final ModifiableCallout<AbilityCastStart> burst = new ModifiableCallout<>("Burst", "Towers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> burstSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5FAC),
			(e1, s) -> {
				// 誘導が終わってから塔が出る
				s.waitMs(15_000);
				s.updateCall(burst, e1);
			});

}
