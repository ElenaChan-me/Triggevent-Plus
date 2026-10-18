package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e8n.ts をもとに移植
@CalloutRepo(name = "Edens Verse Refulgence", duty = KnownDuty.EdensVerseRefulgence)
public class E8n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long LIGHT_RAMPANT_MARKER = 0x17;

	private XivState state;

	public E8n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensVerseRefulgence);
	}

	private boolean mirrorsActive;
	private boolean rampantOnMe;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		mirrorsActive = false;
		rampantOnMe = false;
	}

	private final ModifiableCallout<AbilityCastStart> bitingFrost = ModifiableCallout.durationBasedCall("Biting Frost", "Get Behind");
	private final ModifiableCallout<AbilityCastStart> bitingFrostMirror = ModifiableCallout.durationBasedCall("Biting Frost (With Mirror)", "Get Behind, then South");
	private final ModifiableCallout<AbilityCastStart> drivingFrost = ModifiableCallout.durationBasedCall("Driving Frost", "Front/Sides");
	private final ModifiableCallout<AbilityCastStart> drivingFrostMirror = ModifiableCallout.durationBasedCall("Driving Frost (With Mirror)", "Front/Sides, then North");
	private final ModifiableCallout<AbilityCastStart> axeKick = ModifiableCallout.durationBasedCall("Axe Kick", "Out");
	private final ModifiableCallout<AbilityCastStart> axeKickMirror = ModifiableCallout.durationBasedCall("Axe Kick (With Mirror)", "Out, then In");

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x4DD4)) {
			mirrorsActive = true;
		}
		else if (event.abilityIdMatches(0x4DDB)) {
			context.accept((mirrorsActive ? bitingFrostMirror : bitingFrost).getModified(event));
		}
		else if (event.abilityIdMatches(0x4DDC)) {
			context.accept((mirrorsActive ? drivingFrostMirror : drivingFrost).getModified(event));
		}
		else if (event.abilityIdMatches(0x4DE2)) {
			context.accept((mirrorsActive ? axeKickMirror : axeKick).getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> mirrorCleanupSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4DFE, 0x4DFF, 0x4E00, 0x4E01),
			(e1, s) -> {
				// 鏡の攻撃が終わるまで余裕を持たせてから解除する
				s.waitMs(5_000);
				mirrorsActive = false;
			});

	@NpcCastCallout(0x4DE3)
	private final ModifiableCallout<AbilityCastStart> scytheKick = ModifiableCallout.durationBasedCall("Scythe Kick", "Under");

	@NpcCastCallout(value = 0x4E01, suppressMs = 3_000)
	private final ModifiableCallout<AbilityCastStart> reflectedScytheKick = ModifiableCallout.durationBasedCall("Reflected Scythe Kick", "Close to Mirrors");

	@NpcCastCallout(0x4DD7)
	private final ModifiableCallout<AbilityCastStart> absoluteZero = ModifiableCallout.durationBasedCall("Absolute Zero", "Raidwide");

	@NpcCastCallout(0x4DDA)
	private final ModifiableCallout<AbilityCastStart> doubleSlap = ModifiableCallout.durationBasedCall("Double Slap", "Buster on {event.target}");

	@NpcCastCallout(0x4DE1)
	private final ModifiableCallout<AbilityCastStart> diamondFrost = ModifiableCallout.durationBasedCall("Diamond Frost", "Raidwide");

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> frigidWater = new ModifiableCallout<>("Frigid Water", "Flare on You");

	@PlayerHeadmarker(0x60)
	private final ModifiableCallout<HeadMarkerEvent> icicleImpact = new ModifiableCallout<>("Icicle Impact", "Spread");

	@PlayerHeadmarker(0xC5)
	private final ModifiableCallout<HeadMarkerEvent> puddleChase = new ModifiableCallout<>("Puddle Chase", "3x Puddles on You");

	@NpcCastCallout(0x4DEC)
	private final ModifiableCallout<AbilityCastStart> holy = ModifiableCallout.durationBasedCall("Holy", "Out");

	@NpcCastCallout(0x4DED)
	private final ModifiableCallout<AbilityCastStart> holyDivided = ModifiableCallout.durationBasedCall("Holy (Divided)", "In");

	@HandleEvents
	public void rampantCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(LIGHT_RAMPANT_MARKER) && event.getTarget().isThePlayer()) {
			rampantOnMe = true;
		}
	}

	@HandleEvents
	public void rampantCleanup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4E0B)) {
			rampantOnMe = false;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> rampantCone = new ModifiableCallout<>("Light Rampant (Cone)", "Cone on You, Avoid Towers");
	private final ModifiableCallout<HeadMarkerEvent> rampantTower = new ModifiableCallout<>("Light Rampant (Tower)", "Stand in a Tower");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> lightRampantSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(LIGHT_RAMPANT_MARKER),
			(e1, s) -> {
				// マーカーが全員分出揃うのを待つ
				s.waitMs(500);
				s.updateCall(rampantOnMe ? rampantCone : rampantTower, e1);
				// 残りのマーカーで再度呼ばないよう、しばらくトリガーを占有しておく
				s.waitMs(1_500);
			});

	@NpcCastCallout(0x4DD8)
	private final ModifiableCallout<AbilityCastStart> heavenlyStrike = ModifiableCallout.durationBasedCall("Heavenly Strike", "Knockback");

	@NpcCastCallout(0x4DDD)
	private final ModifiableCallout<AbilityCastStart> twinStillness = ModifiableCallout.durationBasedCall("Twin Stillness", "Back, then Front");

	@NpcCastCallout(0x4DDE)
	private final ModifiableCallout<AbilityCastStart> twinSilence = ModifiableCallout.durationBasedCall("Twin Silence", "Front, then Back");

	@NpcCastCallout(0x4DE4)
	private final ModifiableCallout<AbilityCastStart> spitefulDance = ModifiableCallout.durationBasedCall("Spiteful Dance", "Out, then In");

	@NpcCastCallout(0x4DE5)
	private final ModifiableCallout<AbilityCastStart> embitteredDance = ModifiableCallout.durationBasedCall("Embittered Dance", "In, then Out");

}
