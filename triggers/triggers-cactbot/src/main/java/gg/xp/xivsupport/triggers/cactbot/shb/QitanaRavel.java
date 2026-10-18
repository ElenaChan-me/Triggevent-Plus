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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/qitana_ravel.ts をもとに移植
@CalloutRepo(name = "The Qitana Ravel", duty = KnownDuty.TheQitanaRavel)
public class QitanaRavel extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public QitanaRavel(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheQitanaRavel);
	}

	// 1ボス

	@NpcCastCallout(0x3C89)
	private final ModifiableCallout<AbilityCastStart> stonefist = ModifiableCallout.durationBasedCall("Stonefist", "Buster on {event.target}");

	@NpcCastCallout(0x3C8B)
	private final ModifiableCallout<AbilityCastStart> scorn = ModifiableCallout.durationBasedCall("Lozatl's Scorn", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> eeriePillar = new ModifiableCallout<>("Eerie Pillar", "Look for Pillar");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> eeriePillarSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3C8B),
			(e1, s) -> {
				// 全体攻撃の後に柱が出る
				s.waitMs(5_000);
				s.updateCall(eeriePillar, e1);
			});

	private final ModifiableCallout<AbilityCastStart> heatUpRight = new ModifiableCallout<>("Heat Up (Right)", "Stay on Left Flank", 6_000);
	private final ModifiableCallout<AbilityCastStart> heatUpLeft = new ModifiableCallout<>("Heat Up (Left)", "Stay on Right Flank", 6_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> heatUpSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3C8D, 0x3C8E),
			(e1, s) -> {
				// 像が光ってから移動しても間に合う
				s.waitMs(6_000);
				s.updateCall(e1.abilityIdMatches(0x3C8D) ? heatUpRight : heatUpLeft, e1);
			});

	// 2ボス

	@NpcCastCallout(0x3C91)
	private final ModifiableCallout<AbilityCastStart> ripperFang = ModifiableCallout.durationBasedCall("Ripper Fang", "Buster on {event.target}");

	@NpcCastCallout(0x3C92)
	private final ModifiableCallout<AbilityCastStart> soundwave = ModifiableCallout.durationBasedCall("Soundwave", "Raidwide");

	@NpcCastCallout(0x3C93)
	private final ModifiableCallout<AbilityCastStart> subsonics = ModifiableCallout.durationBasedCall("Subsonics", "Raidwide");

	// 3ボス

	@NpcCastCallout(0x3C99)
	private final ModifiableCallout<AbilityCastStart> rend = ModifiableCallout.durationBasedCall("Rend", "Buster on {event.target}");

	@NpcCastCallout(0x3C9B)
	private final ModifiableCallout<AbilityCastStart> glossolalia = ModifiableCallout.durationBasedCall("Glossolalia", "Raidwide");

	private final ModifiableCallout<TetherEvent> houndTether = new ModifiableCallout<>("Hound Out of Heaven", "Run Away from Boss");

	@HandleEvents
	public void houndTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x39) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(houndTether.getModified(event));
		}
	}

	@PlayerHeadmarker(0xAB)
	private final ModifiableCallout<HeadMarkerEvent> viperPoison = new ModifiableCallout<>("Viper Poison", "Drop Poison Outside");

	private final ModifiableCallout<HeadMarkerEvent> confessionStack = new ModifiableCallout<>("Confession of Faith (Stack)", "Stack on {event.target}");

	@HandleEvents
	public void confessionStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(confessionStack.getModified(event));
		}
	}

	@NpcCastCallout(0x3CA1)
	private final ModifiableCallout<AbilityCastStart> confessionSpread = ModifiableCallout.durationBasedCall("Confession of Faith (Spread)", "Spread to Sides");

}
