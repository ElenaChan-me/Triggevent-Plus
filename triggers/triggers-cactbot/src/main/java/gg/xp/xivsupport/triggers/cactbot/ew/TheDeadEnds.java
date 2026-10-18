package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/the_dead_ends.ts をもとに移植
@CalloutRepo(name = "The Dead Ends", duty = KnownDuty.TheDeadEnds)
public class TheDeadEnds extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheDeadEnds(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheDeadEnds);
	}

	private boolean canCleanse() {
		return state.playerJobMatches(job -> job.isHealer() || job == Job.BRD);
	}

	private boolean seenLovingEmbrace;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenLovingEmbrace = false;
	}

	// カウスティック・グレビュオフ

	@NpcCastCallout(0x653C)
	private final ModifiableCallout<AbilityCastStart> miasmata = ModifiableCallout.durationBasedCall("Miasmata", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> certainSolitude = new ModifiableCallout<>("Certain Solitude", "Dorito Stack");

	// 少し遅れて 0x37 のマーカーも付くが、こちらの方が早い
	@HandleEvents
	public void certainSolitude(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x6EBD) && event.getTarget().isThePlayer()) {
			context.accept(certainSolitude.getModified(event));
		}
	}

	@NpcCastCallout(0x6542)
	private final ModifiableCallout<AbilityCastStart> blightedWater = ModifiableCallout.durationBasedCall("Blighted Water", "Stack on {event.target}");

	@NpcCastCallout(value = 0x6544, onYou = true)
	private final ModifiableCallout<AbilityCastStart> befoulment = ModifiableCallout.durationBasedCall("Befoulment", "Spread");

	private final ModifiableCallout<BuffApplied> necrosis = ModifiableCallout.<BuffApplied>durationBasedCall("Necrosis", "Esuna {event.target}").autoIcon();

	@HandleEvents
	public void necrosis(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0xB95) && canCleanse()) {
			context.accept(necrosis.getModified(event));
		}
	}

	@NpcCastCallout(0x6540)
	private final ModifiableCallout<AbilityCastStart> poxFlail = ModifiableCallout.durationBasedCall("Pox Flail", "Buster on {event.target}");

	// ピースキーパー

	@NpcCastCallout(0x6550)
	private final ModifiableCallout<AbilityCastStart> decimation = ModifiableCallout.durationBasedCall("Decimation", "Raidwide");

	@NpcCastCallout(value = 0x6EC7, onYou = true)
	private final ModifiableCallout<AbilityCastStart> infantryDeterrent = ModifiableCallout.durationBasedCall("Infantry Deterrent", "Spread");

	@NpcCastCallout(value = 0x6548, onYou = true)
	private final ModifiableCallout<AbilityCastStart> noFuture = ModifiableCallout.durationBasedCall("No Future", "Spread");

	@NpcCastCallout(0x6EBF)
	private final ModifiableCallout<AbilityCastStart> orderToFire = ModifiableCallout.durationBasedCall("Order to Fire", "Stand Between Bits");

	@NpcCastCallout(0x654B)
	private final ModifiableCallout<AbilityCastStart> eclipsingExhaust = ModifiableCallout.durationBasedCall("Eclipsing Exhaust", "Knockback");

	private final ModifiableCallout<AbilityCastStart> eliminationOnYou = ModifiableCallout.durationBasedCall("Elimination (on You)", "Tank Laser on You");
	private final ModifiableCallout<AbilityCastStart> eliminationHealer = ModifiableCallout.durationBasedCall("Elimination (Healer)", "Tank Laser on {event.target}");
	private final ModifiableCallout<AbilityCastStart> eliminationAvoid = ModifiableCallout.durationBasedCall("Elimination (Avoid)", "Avoid Laser on {event.target}");

	@HandleEvents
	public void elimination(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x654F)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (event.getTarget().isThePlayer()) {
			call = eliminationOnYou;
		}
		else if (state.playerJobMatches(Job::isHealer)) {
			call = eliminationHealer;
		}
		else {
			call = eliminationAvoid;
		}
		context.accept(call.getModified(event));
	}

	// ラーラー

	@NpcCastCallout(0x655E)
	private final ModifiableCallout<AbilityCastStart> warmGlow = ModifiableCallout.durationBasedCall("Warm Glow", "Raidwide");

	@NpcCastCallout(0x655D)
	private final ModifiableCallout<AbilityCastStart> pity = ModifiableCallout.durationBasedCall("Pity", "Buster on {event.target}");

	@NpcCastCallout(0x655A)
	private final ModifiableCallout<AbilityCastStart> benevolence = ModifiableCallout.durationBasedCall("Benevolence", "Stack on {event.target}");

	private final ModifiableCallout<AbilityCastStart> lovingEmbraceLeft = ModifiableCallout.durationBasedCall("Loving Embrace (Left Safe)", "Left");
	private final ModifiableCallout<AbilityCastStart> lovingEmbraceRight = ModifiableCallout.durationBasedCall("Loving Embrace (Right Safe)", "Right");

	@HandleEvents
	public void lovingEmbrace(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x6557, 0x6558)) {
			return;
		}
		// 初回だけボスが中央にいる。2回目以降は端に跳んだボスの方を向いている前提なので左右が逆になる
		boolean left = event.abilityIdMatches(0x6557) != seenLovingEmbrace;
		seenLovingEmbrace = true;
		context.accept((left ? lovingEmbraceLeft : lovingEmbraceRight).getModified(event));
	}

	@NpcCastCallout(value = 0x655C, onYou = true)
	private final ModifiableCallout<AbilityCastStart> stillEmbrace = ModifiableCallout.durationBasedCall("Still Embrace", "Spread");

	private final ModifiableCallout<BuffApplied> doom = ModifiableCallout.<BuffApplied>durationBasedCall("Doom", "Heal {event.target} to Full").autoIcon();

	@HandleEvents
	public void doom(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x6E9) && canCleanse()) {
			context.accept(doom.getModified(event));
		}
	}

}
