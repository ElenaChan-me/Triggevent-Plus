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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/ruby_weapon-ex.ts をもとに移植
@CalloutRepo(name = "Cinder Drift Extreme", duty = KnownDuty.CinderDriftExtreme)
public class RubyWeaponEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long LUNAR_DYNAMO = 0x4EB0;
	private static final long IRON_CHARIOT = 0x4EB1;
	private static final long FIRST_METEOR_MARKER = 0xCA;
	private static final long LAST_METEOR_MARKER = 0xD1;

	private XivState state;

	public RubyWeaponEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.CinderDriftExtreme);
	}

	private enum RavenColor {
		RED,
		BLUE
	}

	private boolean seenFlight;
	private boolean ravenDead;
	// プレイヤーごとの攻撃対象の幻影の色
	private final Map<Long, RavenColor> colors = new HashMap<>();
	private final Map<RavenColor, Long> colorToImageId = new EnumMap<>(RavenColor.class);
	private final Map<Long, Long> imageIdToAction = new HashMap<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenFlight = false;
		ravenDead = false;
		colors.clear();
		colorToImageId.clear();
		imageIdToAction.clear();
	}

	private boolean isTankOrBlu() {
		return state.playerJobMatches(job -> job.isTank() || job == Job.BLU);
	}

	private RavenColor myColor() {
		XivCombatant player = state.getPlayer();
		return player == null ? null : colors.get(player.getId());
	}

	// 前半

	@NpcCastCallout(0x4ABE)
	private final ModifiableCallout<AbilityCastStart> optimizedUltima = ModifiableCallout.durationBasedCall("Optimized Ultima", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> stampOnYou = ModifiableCallout.durationBasedCall("Stamp (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> stampSwap = ModifiableCallout.durationBasedCall("Stamp (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> stamp = ModifiableCallout.durationBasedCall("Stamp", "Buster on {event.target}");

	@HandleEvents
	public void stamp(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4B03)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (event.getTarget().isThePlayer()) {
			call = stampOnYou;
		}
		else if (state.playerJobMatches(Job::isTank)) {
			call = stampSwap;
		}
		else {
			call = stamp;
		}
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x4ACC)
	private final ModifiableCallout<AbilityCastStart> ravensclaw = ModifiableCallout.durationBasedCall("Ravensclaw", "Away From Line Ends");

	@NpcCastCallout(0x4AD0)
	private final ModifiableCallout<AbilityCastStart> undermine = ModifiableCallout.durationBasedCall("Undermine", "Away from Lines");

	@NpcCastCallout(0x4ACF)
	private final ModifiableCallout<AbilityCastStart> liquefaction = ModifiableCallout.durationBasedCall("Liquefaction", "Get On Lines");

	private final ModifiableCallout<AbilityCastStart> ravensflightMiddle = ModifiableCallout.durationBasedCall("Liquefaction (Ravensflight, First)", "Middle");
	private final ModifiableCallout<AbilityCastStart> ravensflightOutOfMiddle = ModifiableCallout.durationBasedCall("Liquefaction (Ravensflight, Later)", "Out of Middle");

	@HandleEvents
	public void liquefactionRavensflight(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4AEC)) {
			context.accept((seenFlight ? ravensflightOutOfMiddle : ravensflightMiddle).getModified(event));
			seenFlight = true;
		}
	}

	@NpcCastCallout(0x4B02)
	private final ModifiableCallout<AbilityCastStart> rubyRay = ModifiableCallout.durationBasedCall("Ruby Ray", "Away from Front");

	@NpcCastCallout(0x4B05)
	private final ModifiableCallout<AbilityCastStart> cutAndRun = ModifiableCallout.durationBasedCall("Cut and Run", "Sides");

	@NpcCastCallout(value = 0x4AD8, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> homingLasers = ModifiableCallout.durationBasedCall("High-Powered Homing Lasers", "Stack");

	// ラストの詠唱はローテーションが終わる前に始まることがあるので、LBの使い忘れを促す
	@NpcCastCallout(0x4B2D)
	private final ModifiableCallout<AbilityCastStart> enrage = ModifiableCallout.durationBasedCall("Optimized Ultima (Enrage)", "Enrage");

	// ラーヴァナの幻影

	private final ModifiableCallout<BuffApplied> pallOfRage = new ModifiableCallout<BuffApplied>("Pall of Rage", "Attack Blue (East)").autoIcon();
	private final ModifiableCallout<BuffApplied> pallOfGrief = new ModifiableCallout<BuffApplied>("Pall of Grief", "Attack Red (West)").autoIcon();

	@HandleEvents
	public void palls(EventContext context, BuffApplied event) {
		boolean rage = event.buffIdMatches(0x8A2);
		if (!rage && !event.buffIdMatches(0x8A3)) {
			return;
		}
		// デバフと逆の色の幻影を攻撃する
		colors.put(event.getTarget().getId(), rage ? RavenColor.BLUE : RavenColor.RED);
		if (event.getTarget().isThePlayer()) {
			context.accept((rage ? pallOfRage : pallOfGrief).getModified(event));
		}
	}

	@PlayerHeadmarker(0xE0)
	private final ModifiableCallout<HeadMarkerEvent> meteorStream = new ModifiableCallout<>("Meteor Stream", "Spread");

	private final ModifiableCallout<AbilityCastStart> rubyClawOnYou = ModifiableCallout.durationBasedCall("Ruby Claw (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> rubyClaw = ModifiableCallout.durationBasedCall("Ruby Claw", "Buster on {event.target}");
	private final RepeatSuppressor rubyClawSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void rubyClaw(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4AFF)) {
			return;
		}
		if (state.playerJobMatches(job -> job.isDps() && job != Job.BLU)) {
			return;
		}
		XivCombatant target = event.getTarget();
		RavenColor mine = myColor();
		// 自分と同じ色を攻撃している側への攻撃だけを知らせる
		boolean relevant = target.isThePlayer() || (mine != null && mine == colors.get(target.getId()));
		if (relevant && rubyClawSupp.check(event)) {
			context.accept((target.isThePlayer() ? rubyClawOnYou : rubyClaw).getModified(event));
		}
	}

	@HandleEvents
	public void ravenDeath(EventContext context, BuffRemoved event) {
		// このデバフは死亡しても残り、撃破の少し前に全員から外れる
		if (event.buffIdMatches(0x8A3)) {
			ravenDead = true;
		}
	}

	@HandleEvents
	public void imageColors(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x8A0, 0x8A1)) {
			colorToImageId.put(event.buffIdMatches(0x8A1) ? RavenColor.BLUE : RavenColor.RED, event.getTarget().getId());
		}
	}

	@HandleEvents
	public void imageActionCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(LUNAR_DYNAMO, IRON_CHARIOT)) {
			imageIdToAction.put(event.getSource().getId(), event.getAbility().getId());
		}
	}

	private final ModifiableCallout<AbilityCastStart> imageIn = ModifiableCallout.durationBasedCall("Lunar Dynamo", "In ({suffix})");
	private final ModifiableCallout<AbilityCastStart> imageOut = ModifiableCallout.durationBasedCall("Iron Chariot", "Out ({suffix})");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> imageChariotDynamoSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(LUNAR_DYNAMO, IRON_CHARIOT),
			(e1, s) -> {
				// 2体の詠唱が揃うのを待つ
				s.waitMs(100);
				Map<RavenColor, Long> colorToAction = new EnumMap<>(RavenColor.class);
				colorToImageId.forEach((color, imageId) -> {
					Long action = imageIdToAction.get(imageId);
					if (action != null) {
						colorToAction.put(color, action);
					}
				});
				imageIdToAction.clear();
				RavenColor mine = myColor();
				String suffix;
				Long actionId;
				if (colorToAction.size() == 2 && colorToAction.get(RavenColor.BLUE).equals(colorToAction.get(RavenColor.RED))) {
					suffix = "both";
					actionId = colorToAction.get(RavenColor.BLUE);
				}
				else if (colorToAction.size() == 1) {
					RavenColor color = colorToAction.keySet().iterator().next();
					suffix = color == RavenColor.BLUE ? "blue" : "red";
					actionId = colorToAction.get(color);
				}
				else if (mine != null && colorToAction.containsKey(mine)) {
					suffix = mine == RavenColor.BLUE ? "blue" : "red";
					actionId = colorToAction.get(mine);
				}
				else {
					// 色が分からない状態で別々の技を言うと混乱するので何も言わない
					return;
				}
				s.setParam("suffix", suffix);
				s.updateCall(actionId == LUNAR_DYNAMO ? imageIn : imageOut, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> changeOfHeartRed = new ModifiableCallout<>("Change of Heart (Red)", "Attack Red (East)");
	private final ModifiableCallout<AbilityUsedEvent> changeOfHeartBlue = new ModifiableCallout<>("Change of Heart (Blue)", "Attack Blue (West)");
	private final RepeatSuppressor changeOfHeartSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void changeOfHeart(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x4AFC) || !changeOfHeartSupp.check(event) || ravenDead) {
			return;
		}
		RavenColor mine = myColor();
		if (mine != null) {
			context.accept((mine == RavenColor.RED ? changeOfHeartRed : changeOfHeartBlue).getModified(event));
		}
	}

	@NpcCastCallout(0x4AFE)
	private final ModifiableCallout<AbilityCastStart> negativeAura = ModifiableCallout.durationBasedCall("Negative Aura", "Look Away");

	// 後半

	private final ModifiableCallout<HeadMarkerEvent> meteorNumber = new ModifiableCallout<>("Meteor Number", "{num}");

	@HandleEvents
	public void meteorNumber(EventContext context, HeadMarkerEvent event) {
		long id = event.getMarkerId();
		if (event.getTarget().isThePlayer() && id >= FIRST_METEOR_MARKER && id <= LAST_METEOR_MARKER) {
			context.accept(meteorNumber.getModified(event, Map.<String, Object>of("num", id - FIRST_METEOR_MARKER + 1)));
		}
	}

	@NpcCastCallout(0x4AEE)
	private final ModifiableCallout<AbilityCastStart> screech = ModifiableCallout.durationBasedCall("Screech", "Knockback");

	@NpcCastCallout(0x4AF0)
	private final ModifiableCallout<AbilityCastStart> magitekMeteorBurst = ModifiableCallout.durationBasedCall("Magitek Meteor Burst", "Away from Meteor");

	private final ModifiableCallout<AbilityUsedEvent> cometTank = new ModifiableCallout<>("Mark II Magitek Comet (Tank)", "Stand in Meteor Tankbuster");
	private final ModifiableCallout<AbilityUsedEvent> cometOther = new ModifiableCallout<>("Mark II Magitek Comet", "Kill Meteor Adds");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> magitekCometSq = SqtTemplates.sq(20_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4AB6),
			(e1, s) -> {
				s.waitMs(11_500);
				if (isTankOrBlu()) {
					s.updateCall(cometTank, e1);
				}
				s.waitMs(1_500);
				if (!state.playerJobMatches(Job::isTank)) {
					s.updateCall(cometOther, e1);
				}
			});

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> bradamante = new ModifiableCallout<>("Bradamante", "Avoid Tanks with Laser");

	@NpcCastCallout(0x4B04)
	private final ModifiableCallout<AbilityCastStart> outrage = ModifiableCallout.durationBasedCall("Outrage", "Raidwide");

}
