The LC FTB hook mod is a mod that connects the lightmans currency mod with the FTB chunks and FTB teams mod, providing a connection between the mod when it comes to buying claims and maintaining/upkeeping their protection. The mod is built for NeoForge version 21.1.231 for the Minecraft version 1.21.1.

Chunks can be claimed as a FTB party/team or as a sigle player. In both cases, we have to decide, what the money source is. All players have at least one lightman bank account. In case of buying claims as a single player, this account is used for purchasing and for paying the protection upkeep. For teams, this procedure is a bit more complicated, as a team must have an associated account at all times. The mod ensures a continuous check for all the present teams. For each team an undeletable account must exist that mirrors the team roles to the lightman roles for the account. The tea owner must therefore be also the owner of the account, officers must be admins and member are members. 

Once a player buys a new claim, the balance of the single player account is checked and the correct amount is withdrawn. The amount must be configurable. The same thing happens when buying force loads. The price of those two types of purchasables can be different. Only owners and officers of a team can perform those buying actions and the correct amount is withdrawn as well.

The upkeep is a bit more complicated and follows a simple calculation. Let n be the number of claimed chunks a team or a player has. The cost c for an upkeep period is calculated as follows:

c = b * n

where b is the base price. The base price depends on what protection attributes are chosen in the FTB teams interface. The protections that should cost something is the mob grief protection (p1), explosion protection (p2), pvp disablement (p3), block interaction mode (p4) and block edit mode (p5). For each of those protections a price can be set in the servers config. The base price is the sum of all of these prices. p1, p2 and p3 are boolean values. If they are set to false, their price is added to the calculation. p4 and p3 are added to the calculation once they are NOT set to public.

![FTB Chunks protection properties](images/Screenshot%202026-06-11%20195621.png)

If a player account or the team account dont have enough money, all the protection should be set to their lowest values. p1, p2 and p3 should all be true then and p4 and p5 set to public. When the account balance is enough for the next period (which is configured), players can change the protections again. E.g. if no protection is selected and a player selects explosion protection to be activated (set to false), the mod calculates the new base price and thus the cost. If the balance is met, the protection change is enabled. If not, the change does not apply (visually there is no switch) and in the menu presented in the image an alert pops up.

