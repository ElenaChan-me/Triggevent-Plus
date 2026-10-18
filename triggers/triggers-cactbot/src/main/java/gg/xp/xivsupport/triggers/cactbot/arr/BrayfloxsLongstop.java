package gg.xp.xivsupport.triggers.cactbot.arr;

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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/brayfloxs_longstop.ts をもとに移植
@CalloutRepo(name = "Brayfloxs Longstop", duty = KnownDuty.BrayfloxsLongstop)
public class BrayfloxsLongstop extends AutoChildEventHandler implements FilteredEventHandler {

	// どの湧きにも最低1体はいるので、サブルバックの出現で雑魚湧きを判定する
	private static final long SABLE_BACK = 1283;
	private static final long TEMPEST_BIAST = 1285;

	private XivState state;

	public BrayfloxsLongstop(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.BrayfloxsLongstop);
	}

	private boolean canStun() {
		return state.playerJobMatches(job -> job.isTank() || job.isMeleeDps());
	}

	private boolean canCleanse() {
		return state.playerJobMatches(job -> job.isHealer() || job == Job.BRD);
	}

	private final ModifiableCallout<AbilityCastStart> numbingBreath = ModifiableCallout.durationBasedCall("Numbing Breath", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> ashdrakeBurningCyclone = ModifiableCallout.durationBasedCall("Burning Cyclone (Ashdrake)", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> infernoDrakeBurningCyclone = ModifiableCallout.durationBasedCall("Burning Cyclone (Inferno Drake)", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> dragonBreath = ModifiableCallout.durationBasedCall("Dragon Breath", "Stun {event.source}");

	@HandleEvents
	public void stuns(EventContext context, AbilityCastStart event) {
		if (!canStun()) {
			return;
		}
		switch ((int) event.getAbility().getId()) {
			case 0x1FA -> context.accept(numbingBreath.getModified(event));
			case 0x205 -> context.accept(ashdrakeBurningCyclone.getModified(event));
			case 0x3D8 -> context.accept(infernoDrakeBurningCyclone.getModified(event));
			case 0x22F -> context.accept(dragonBreath.getModified(event));
		}
	}

	private final List<XivCombatant> pelicanPoisons = new ArrayList<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		pelicanPoisons.clear();
	}

	@HandleEvents
	public void pelicanPoisonCollect(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x12) && canCleanse()) {
			pelicanPoisons.add(event.getTarget());
		}
	}

	private final ModifiableCallout<BuffApplied> esunaYourPoison = new ModifiableCallout<BuffApplied>("Pelican Poison (on You)", "Esuna Your Poison").autoIcon();
	private final ModifiableCallout<BuffApplied> esunaPoisonOn = new ModifiableCallout<BuffApplied>("Pelican Poison", "Esuna Poison on {players}").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> pelicanPoisonSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x12) && canCleanse(),
			(e1, s) -> {
				// ほぼ同時に複数人へ付与されるので少し待ってからまとめて呼ぶ
				s.waitMs(1_000);
				List<XivCombatant> targets = new ArrayList<>(pelicanPoisons);
				pelicanPoisons.clear();
				if (targets.isEmpty()) {
					targets.add(e1.getTarget());
				}
				if (targets.size() == 1 && targets.get(0).isThePlayer()) {
					s.updateCall(esunaYourPoison, e1);
				}
				else {
					s.setParam("players", targets.stream().map(XivCombatant::getName).sorted().collect(Collectors.joining(", ")));
					s.updateCall(esunaPoisonOn, e1);
				}
			});

	private final ModifiableCallout<RawAddCombatantEvent> pelicanAdds = new ModifiableCallout<>("Pelican Adds", "Kill Adds");
	private final ModifiableCallout<RawAddCombatantEvent> tempestBiast = new ModifiableCallout<>("Tempest Biast", "Kill Adds");
	private final RepeatSuppressor pelicanAddsSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		long nameId = event.getEntity().getbNpcNameId();
		if (nameId == SABLE_BACK) {
			if (pelicanAddsSupp.check(event)) {
				context.accept(pelicanAdds.getModified(event));
			}
		}
		else if (nameId == TEMPEST_BIAST) {
			context.accept(tempestBiast.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> breakBubbleOn = new ModifiableCallout<>("Effluvium", "Break Bubble on {event.target}");
	private final ModifiableCallout<AbilityUsedEvent> breakYourBubble = new ModifiableCallout<>("Effluvium (on You)", "Break Your Bubble");

	@HandleEvents
	public void effluvium(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x3D3) && !event.getSource().isPc()) {
			if (event.getTarget().isThePlayer()) {
				context.accept(breakYourBubble.getModified(event));
			}
			else {
				context.accept(breakBubbleOn.getModified(event));
			}
		}
	}

	private final ModifiableCallout<BuffApplied> toxicVomitTank = new ModifiableCallout<>("Toxic Vomit (Tank)", "Move Boss Out of Puddles");
	private final RepeatSuppressor toxicVomitSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void toxicVomit(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x117) && state.playerJobMatches(Job::isTank) && toxicVomitSupp.check(event)) {
			context.accept(toxicVomitTank.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> aiatarPoisonOn = new ModifiableCallout<BuffApplied>("Aiatar Poison", "Esuna Poison on {event.target}").autoIcon();
	private final ModifiableCallout<BuffApplied> aiatarPoisonYou = new ModifiableCallout<BuffApplied>("Aiatar Poison (on You)", "Esuna Your Poison").autoIcon();

	@HandleEvents
	public void aiatarPoison(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x113) && canCleanse()) {
			if (event.getTarget().isThePlayer()) {
				context.accept(aiatarPoisonYou.getModified(event));
			}
			else {
				context.accept(aiatarPoisonOn.getModified(event));
			}
		}
	}

	@NpcCastCallout(0x6FF3)
	private final ModifiableCallout<AbilityCastStart> salivousSnap = ModifiableCallout.durationBasedCall("Salivous Snap", "Buster on {event.target}");
}
