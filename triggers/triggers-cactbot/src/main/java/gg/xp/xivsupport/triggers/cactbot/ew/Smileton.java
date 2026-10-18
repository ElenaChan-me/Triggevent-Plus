package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/smileton.ts をもとに移植
@CalloutRepo(name = "Smileton", duty = KnownDuty.Smileton)
public class Smileton extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SMILEY_FACE = 0xACB;
	private static final long FROWNY_FACE = 0xACC;

	private XivState state;

	public Smileton(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Smileton);
	}

	private boolean smileyFace;
	private boolean frownyFace;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		smileyFace = false;
		frownyFace = false;
	}

	// フェイス

	@NpcCastCallout(0x673E)
	private final ModifiableCallout<AbilityCastStart> offMyLawn = ModifiableCallout.durationBasedCall("Off My Lawn", "Knockback (One Row)");

	@NpcCastCallout(0x6743)
	private final ModifiableCallout<AbilityCastStart> tempersFlare = ModifiableCallout.durationBasedCall("Temper's Flare", "Raidwide");

	@NpcCastCallout(0x6742)
	private final ModifiableCallout<AbilityCastStart> heartOnFire = ModifiableCallout.durationBasedCall("Heart on Fire IV", "Buster on {event.target}");

	@HandleEvents
	public void faceGained(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(SMILEY_FACE)) {
			smileyFace = true;
		}
		else if (event.buffIdMatches(FROWNY_FACE)) {
			frownyFace = true;
		}
	}

	@HandleEvents
	public void faceRemoved(EventContext context, BuffRemoved event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(SMILEY_FACE)) {
			smileyFace = false;
		}
		else if (event.buffIdMatches(FROWNY_FACE)) {
			frownyFace = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> mixedFeelingsSmiley = ModifiableCallout.durationBasedCall("Mixed Feelings (Frowny Face)", "Get Hit by Blue Smiley");
	private final ModifiableCallout<AbilityCastStart> mixedFeelingsFrowny = ModifiableCallout.durationBasedCall("Mixed Feelings (Smiley Face)", "Get Hit by Red Frowny");
	private final ModifiableCallout<AbilityCastStart> mixedFeelingsEither = ModifiableCallout.durationBasedCall("Mixed Feelings (No Face)", "Get Hit by Red or Blue Face");
	private final RepeatSuppressor mixedFeelingsSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void mixedFeelings(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x6738) || !mixedFeelingsSupp.check(event)) {
			return;
		}
		// 自分に付いている顔と逆の色の顔に当たって打ち消す
		ModifiableCallout<AbilityCastStart> call;
		if (smileyFace) {
			call = mixedFeelingsFrowny;
		}
		else if (frownyFace) {
			call = mixedFeelingsSmiley;
		}
		else {
			call = mixedFeelingsEither;
		}
		context.accept(call.getModified(event));
	}

	// フレームワーカー

	@NpcCastCallout(0x6745)
	private final ModifiableCallout<AbilityCastStart> circularSaw = ModifiableCallout.durationBasedCall("Circular Saw", "Raidwide");

	@NpcCastCallout(0x6744)
	private final ModifiableCallout<AbilityCastStart> steelBeam = ModifiableCallout.durationBasedCall("Steel Beam", "Buster on {event.target}");

	// ビッグチーズ

	@NpcCastCallout(0x6752)
	private final ModifiableCallout<AbilityCastStart> violentDischarge = ModifiableCallout.durationBasedCall("Violent Discharge", "Raidwide");

	@NpcCastCallout(0x674F)
	private final ModifiableCallout<AbilityCastStart> rightDisassembler = ModifiableCallout.durationBasedCall("Right Disassembler", "Right");

	@NpcCastCallout(0x6750)
	private final ModifiableCallout<AbilityCastStart> leftDisassembler = ModifiableCallout.durationBasedCall("Left Disassembler", "Left");

	@NpcCastCallout(0x6751)
	private final ModifiableCallout<AbilityCastStart> piercingMissile = ModifiableCallout.durationBasedCall("Piercing Missile", "Buster on {event.target}");

	@NpcCastCallout(value = 0x6755, onYou = true)
	private final ModifiableCallout<AbilityCastStart> levelingMissile = ModifiableCallout.durationBasedCall("Leveling Missile", "Spread");

	@NpcCastCallout(0x6753)
	private final ModifiableCallout<AbilityCastStart> electricArc = ModifiableCallout.durationBasedCall("Electric Arc", "Stack on {event.target}");

}
