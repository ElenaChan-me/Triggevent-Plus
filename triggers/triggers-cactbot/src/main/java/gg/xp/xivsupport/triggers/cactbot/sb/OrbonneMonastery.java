package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/alliance/orbonne_monastery.ts をもとに移植
@CalloutRepo(name = "The Orbonne Monastery", duty = KnownDuty.TheOrbonneMonastery)
public class OrbonneMonastery extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public OrbonneMonastery(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheOrbonneMonastery);
	}

	private boolean agriasGhostCleanse;
	private boolean playerInHalidom;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		agriasGhostCleanse = false;
		playerInHalidom = false;
	}

	// 道中

	@NpcCastCallout(value = 0x3778, suppressMs = 10_000)
	private final ModifiableCallout<AbilityCastStart> devitalize = ModifiableCallout.durationBasedCall("Devitalize", "Look Away");

	// ムスタディオ

	@NpcCastCallout(0x373E)
	private final ModifiableCallout<AbilityCastStart> rightHandgonne = ModifiableCallout.durationBasedCall("Right Handgonne", "Left");

	@NpcCastCallout(0x373F)
	private final ModifiableCallout<AbilityCastStart> leftHandgonne = ModifiableCallout.durationBasedCall("Left Handgonne", "Right");

	@NpcCastCallout(0x3737)
	private final ModifiableCallout<AbilityCastStart> lastTestament = ModifiableCallout.durationBasedCall("Last Testament", "Point Opening at Mustadio");

	@NpcCastCallout(0x3739)
	private final ModifiableCallout<AbilityCastStart> armShot = ModifiableCallout.durationBasedCall("Arm Shot", "Buster on {event.target}");

	@PlayerHeadmarker(0xA4)
	private final ModifiableCallout<HeadMarkerEvent> searchlight = new ModifiableCallout<>("Searchlight", "Searchlight on You");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> spreadMarker = new ModifiableCallout<>("Spread Marker", "Spread");

	// アグリアス

	@NpcCastCallout(0x3866)
	private final ModifiableCallout<AbilityCastStart> thunderSlash = ModifiableCallout.durationBasedCall("Thunder Slash", "Tank Cleave");

	// 吸引マーカーの人は剣を拾わずにギミックを処理するので、聖域処理の間はコールを止める
	@AutoFeed
	private final SequentialTrigger<BaseEvent> cleansingStrikeSq = SqtTemplates.sq(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3854),
			(e1, s) -> {
				playerInHalidom = false;
				s.waitMs(50_000);
				agriasGhostCleanse = false;
			});

	@HandleEvents
	public void vacuum(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xA5) && event.getTarget().isThePlayer()) {
			agriasGhostCleanse = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> consecration = ModifiableCallout.durationBasedCall("Consecration", "Pick Up Swords");

	@HandleEvents
	public void consecration(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3850) && !agriasGhostCleanse) {
			context.accept(consecration.getModified(event));
		}
	}

	@HandleEvents
	public void halidomHit(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x3851) && event.getTarget().isThePlayer()) {
			playerInHalidom = true;
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> halidomOutside = new ModifiableCallout<>("Halidom: Not Jailed", "Use Swords on Jails");
	private final RepeatSuppressor halidomSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> halidomSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x3851) && halidomSupp.check(aue),
			(e1, s) -> {
				// 全員分の捕縛が届くのを待ってから判定する
				s.waitMs(500);
				if (!agriasGhostCleanse && !playerInHalidom) {
					s.updateCall(halidomOutside, e1);
				}
			});

	@PlayerHeadmarker(0xA6)
	private final ModifiableCallout<HeadMarkerEvent> agriasHallowedBolt = new ModifiableCallout<>("Agrias Hallowed Bolt", "Go to Center");

	@NpcAbilityUsedCallout(0x385D)
	private final ModifiableCallout<AbilityUsedEvent> addsPhase = new ModifiableCallout<>("Adds Phase", "Get Shield");

	@NpcCastCallout(value = 0x385E, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> mortalBlow = ModifiableCallout.durationBasedCall("Mortal Blow", "Use Shield, Face Knights");

	@NpcCastCallout(0x3857)
	private final ModifiableCallout<AbilityCastStart> judgmentBlade = ModifiableCallout.durationBasedCall("Judgment Blade", "Use Shield, Face Boss");

	@NpcCastCallout(0x3858)
	private final ModifiableCallout<AbilityCastStart> agriasDivineRuination = ModifiableCallout.durationBasedCall("Agrias Divine Ruination", "Use Shield if Tethered");

	// 雷神シド

	private final ModifiableCallout<AbilityCastStart> crushHelmHealer = ModifiableCallout.durationBasedCall("Crush Helm (Healer)", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> crushHelmFeint = ModifiableCallout.durationBasedCall("Crush Helm (Feint)", "Feint Tankbuster");

	@HandleEvents
	public void crushHelmCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x3752)) {
			return;
		}
		if (state.playerJobMatches(Job::isHealer)) {
			context.accept(crushHelmHealer.getModified(event));
		}
		else if (state.playerJobMatches(job -> job.isMeleeDps() || job == Job.BLU)) {
			context.accept(crushHelmFeint.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> crushHelmTank = new ModifiableCallout<>("Crush Helm (Hit on You)", "Tankbuster");
	private final RepeatSuppressor crushHelmSupp = new RepeatSuppressor(Duration.ofSeconds(3));

	@HandleEvents
	public void crushHelmHit(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x3753) && event.getTarget().isThePlayer() && crushHelmSupp.check(event)) {
			context.accept(crushHelmTank.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> crushArmorTank = ModifiableCallout.durationBasedCall("Crush Armor (Tank)", "Give Tether Away");

	@HandleEvents
	public void crushArmorCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3758) && state.playerJobMatches(Job::isTank)) {
			context.accept(crushArmorTank.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> crushArmorHit = new ModifiableCallout<>("Crush Armor (Hit on You)", "Give Tether Away");

	@HandleEvents
	public void crushArmorHit(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x3759) && event.getTarget().isThePlayer()) {
			context.accept(crushArmorHit.getModified(event));
		}
	}

	@NpcCastCallout(0x375A)
	private final ModifiableCallout<AbilityCastStart> crushAccessory = ModifiableCallout.durationBasedCall("Crush Accessory", "Kill Icewolf Adds");

	private final ModifiableCallout<AbilityUsedEvent> cidCleansingStrike = new ModifiableCallout<>("Cid Cleansing Strike", "Heal to Full");
	private final RepeatSuppressor cidCleansingStrikeSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void cidCleansingStrike(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x3751)
				&& state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)
				&& cidCleansingStrikeSupp.check(event)) {
			context.accept(cidCleansingStrike.getModified(event));
		}
	}

	@NpcCastCallout(0x3761)
	private final ModifiableCallout<AbilityCastStart> shadowblade = ModifiableCallout.durationBasedCall("Shadowblade", "Stand on Pads");

	@PlayerHeadmarker(0xAA)
	private final ModifiableCallout<HeadMarkerEvent> shadowbladeBubble = new ModifiableCallout<>("Shadowblade: Bubble", "Drop Bubble in Back");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> cidHallowedBolt = new ModifiableCallout<>("Cid Hallowed Bolt", "Bolt on You");

	@PlayerHeadmarker(0x5C)
	private final ModifiableCallout<HeadMarkerEvent> crushWeapon = new ModifiableCallout<>("Crush Weapon", "Out");

	private final ModifiableCallout<HeadMarkerEvent> hallowedBoltStack = new ModifiableCallout<>("Hallowed Bolt: Stack", "Stack");
	private final RepeatSuppressor hallowedBoltStackSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void hallowedBoltStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E) && hallowedBoltStackSupp.check(event)) {
			context.accept(hallowedBoltStack.getModified(event));
		}
	}

	@PlayerHeadmarker(0x6E)
	private final ModifiableCallout<HeadMarkerEvent> cidDivineRuination = new ModifiableCallout<>("Cid Divine Ruination", "Point Line Outside");

	@NpcCastCallout(0x3750)
	private final ModifiableCallout<AbilityCastStart> holySwordIn = ModifiableCallout.durationBasedCall("Holy Sword (In)", "In");

	@NpcCastCallout(0x374F)
	private final ModifiableCallout<AbilityCastStart> holySwordOut = ModifiableCallout.durationBasedCall("Holy Sword (Out)", "Out");

	@NpcCastCallout(0x3749)
	private final ModifiableCallout<AbilityCastStart> holySwordThunderLeft = ModifiableCallout.durationBasedCall("Holy Sword (Thunder, Left Safe)", "Left");

	@NpcCastCallout(0x374A)
	private final ModifiableCallout<AbilityCastStart> holySwordThunderRight = ModifiableCallout.durationBasedCall("Holy Sword (Thunder, Right Safe)", "Right");

	@NpcCastCallout(0x374C)
	private final ModifiableCallout<AbilityCastStart> holySwordThreeRight = ModifiableCallout.durationBasedCall("Holy Sword (Three, Rotate Right)", "Rotate Right");

	@NpcCastCallout(0x374D)
	private final ModifiableCallout<AbilityCastStart> holySwordThreeLeft = ModifiableCallout.durationBasedCall("Holy Sword (Three, Rotate Left)", "Rotate Left");

	// 聖天使アルテマ

	@NpcCastCallout(0x38AA)
	private final ModifiableCallout<AbilityCastStart> redemption = ModifiableCallout.durationBasedCall("Redemption", "Buster on {event.target}");

	@PlayerHeadmarker(0x37)
	private final ModifiableCallout<HeadMarkerEvent> darkCannonade = new ModifiableCallout<>("Dark Cannonade", "Dorito Stack");

	@PlayerHeadmarker(0x66)
	private final ModifiableCallout<HeadMarkerEvent> eruption = new ModifiableCallout<>("Eruption", "Eruption on You");

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> flareIV = new ModifiableCallout<>("Flare IV", "Out");

	@NpcCastCallout(0x38CF)
	private final ModifiableCallout<AbilityCastStart> timeEruption = ModifiableCallout.durationBasedCall("Time Eruption", "Stand on Slow Clock");

	@NpcCastCallout(0x38DA)
	private final ModifiableCallout<AbilityCastStart> extremeEdge = ModifiableCallout.durationBasedCall("Extreme Edge", "Look for Hashmal Dash");

	private final ModifiableCallout<AbilityCastStart> ultimateIllusionHealer = ModifiableCallout.durationBasedCall("Ultimate Illusion (Healer)", "Heal Hard");

	@HandleEvents
	public void ultimateIllusionCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3895) && state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)) {
			context.accept(ultimateIllusionHealer.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> killRuination = new ModifiableCallout<>("Ultimate Illusion: Kill Ruination", "Kill Ruination");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ultimateIllusionSq = SqtTemplates.sq(30_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x3895),
			(e1, s) -> {
				if (state.playerJobMatches(Job::isHealer)) {
					return;
				}
				// 堕天の証が攻撃可能になるまで時間がかかる
				s.waitMs(23_500);
				s.updateCall(killRuination, e1);
			});

	private final ModifiableCallout<BuffApplied> accelerationBomb = new ModifiableCallout<BuffApplied>("Acceleration Bomb", "Stop Everything").statusIcon(0x430);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> accelerationBombSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x430) && ba.getTarget().isThePlayer(), accelerationBomb, Duration.ofSeconds(1));

}
