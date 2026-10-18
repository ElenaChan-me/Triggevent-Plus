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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/hades.ts をもとに移植
@CalloutRepo(name = "The Dying Gasp", duty = KnownDuty.TheDyingGasp)
public class Hades extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long TITANOMACHY = 0x4180;
	private static final long SPREAD_MARKER = 0x60;
	private static final long STACK_MARKER = 0x3E;

	private XivState state;

	public Hades(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheDyingGasp);
	}

	private boolean neoHades;
	private boolean seenLifeInCaptivity;
	private boolean ancientMarkerOnMe;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		neoHades = false;
		seenLifeInCaptivity = false;
		ancientMarkerOnMe = false;
	}

	@NpcCastCallout(TITANOMACHY)
	private final ModifiableCallout<AbilityCastStart> titanomachy = ModifiableCallout.durationBasedCall("Titanomachy", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> ravenousAssaultOnYou = ModifiableCallout.durationBasedCall("Ravenous Assault (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> ravenousAssaultHealer = ModifiableCallout.durationBasedCall("Ravenous Assault (Healer)", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> ravenousAssault = ModifiableCallout.durationBasedCall("Ravenous Assault", "Away from {event.target}");

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(TITANOMACHY)) {
			neoHades = true;
		}
		else if (event.abilityIdMatches(0x4158)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(ravenousAssaultOnYou.getModified(event));
			}
			else if (state.playerJobMatches(Job::isHealer)) {
				context.accept(ravenousAssaultHealer.getModified(event));
			}
			else {
				context.accept(ravenousAssault.getModified(event));
			}
		}
	}

	@NpcCastCallout({0x4149, 0x4163})
	private final ModifiableCallout<AbilityCastStart> goLeft = ModifiableCallout.durationBasedCall("Bad Faith / Echo (Left)", "Left");

	@NpcCastCallout({0x414A, 0x4164})
	private final ModifiableCallout<AbilityCastStart> goRight = ModifiableCallout.durationBasedCall("Bad Faith / Echo (Right)", "Right");

	@NpcCastCallout(0x414D)
	private final ModifiableCallout<AbilityCastStart> brokenFaith = ModifiableCallout.durationBasedCall("Broken Faith", "Dodge Giant Circles");

	@NpcCastCallout(0x415C)
	private final ModifiableCallout<AbilityCastStart> shadowStream = ModifiableCallout.durationBasedCall("Shadow Stream", "Sides");

	@NpcCastCallout(0x4170)
	private final ModifiableCallout<AbilityCastStart> purgation = ModifiableCallout.durationBasedCall("Purgation", "Middle");

	@PlayerStatusCallout(0xD2)
	private final ModifiableCallout<BuffApplied> doom = new ModifiableCallout<BuffApplied>("Doom", "Cleanse Doom in Circle").statusIcon(0xD2);

	@NpcCastCallout(0x4166)
	private final ModifiableCallout<AbilityCastStart> wailRight = ModifiableCallout.durationBasedCall("Wail of the Lost (Right)", "Right Knockback");

	@NpcCastCallout(0x4165)
	private final ModifiableCallout<AbilityCastStart> wailLeft = ModifiableCallout.durationBasedCall("Wail of the Lost (Left)", "Left Knockback");

	@NpcCastCallout(0x4161)
	private final ModifiableCallout<AbilityCastStart> dualStrike = ModifiableCallout.durationBasedCall("Dual Strike", "Tankbusters");

	@PlayerHeadmarker(0x28)
	private final ModifiableCallout<HeadMarkerEvent> hellbornYawp = new ModifiableCallout<>("Hellborn Yawp", "Drop Marker Outside");

	@PlayerHeadmarker(0x78)
	private final ModifiableCallout<HeadMarkerEvent> fetters = new ModifiableCallout<>("Fetters", "Fetters on You");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> netherBlast = new ModifiableCallout<>("Nether Blast / Dark Eruption", "Spread");

	@PlayerHeadmarker(STACK_MARKER)
	private final ModifiableCallout<HeadMarkerEvent> ancientWater = new ModifiableCallout<>("Ancient Water III", "Stack on You");

	private final ModifiableCallout<HeadMarkerEvent> dualStrikeSpread = new ModifiableCallout<>("Dual Strike Marker", "Tankbuster Spread");
	private final ModifiableCallout<HeadMarkerEvent> ancientDarkness = new ModifiableCallout<>("Ancient Darkness", "Spread, Don't Stack");

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		boolean onMe = event.getTarget().isThePlayer();
		if (event.markerIdMatches(SPREAD_MARKER) && onMe) {
			// 同じマーカーが前半は散開、後半はタンク強攻撃の散開に使われる
			context.accept((neoHades ? dualStrikeSpread : ancientDarkness).getModified(event));
		}
		if (event.markerIdMatches(SPREAD_MARKER, STACK_MARKER) && onMe && !neoHades) {
			ancientMarkerOnMe = true;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> ancientNoMarker = new ModifiableCallout<>("Ancient Water III (No Marker)", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ancientNoMarkerSq = SqtTemplates.sq(15_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(STACK_MARKER),
			(e1, s) -> {
				// 散開マーカーも同時に付くので、出揃ってから判定する
				s.waitMs(500);
				if (!neoHades && !ancientMarkerOnMe) {
					s.updateCall(ancientNoMarker, e1);
				}
				s.waitMs(9_500);
				ancientMarkerOnMe = false;
			});

	@HandleEvents
	public void lifeInCaptivity(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4175)) {
			seenLifeInCaptivity = true;
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> gaol = new ModifiableCallout<>("Gaol", "Kill Jail");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> gaolSq = SqtTemplates.sq(5_000,
			// 最終フェーズへの移行時にも同じアビリティが使われるので、それは除外する
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x417F) && aue.isFirstTarget() && !seenLifeInCaptivity,
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(gaol, e1);
			});

}
